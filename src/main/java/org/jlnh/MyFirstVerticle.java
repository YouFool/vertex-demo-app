package org.jlnh;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLOptions;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.jlnh.model.Article;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static org.jlnh.ActionHelper.*;


/**
 * My first verticle.
 *
 * @author João Heckmann
 */
public class MyFirstVerticle extends AbstractVerticle {

    // Create a router object
    private Router router;

    private JDBCClient jdbc;

    // let's deploy the verticle
    @Override
    public void start(Future<Void> fut) {
        //Instantiate the router
        this.router = Router.router(vertx);

        //Let`s bind "/" to our hello message
        router.route("/").handler(handleEvent -> {
            HttpServerResponse response = handleEvent.response();
            response //
                    .putHeader("content-type", "text/html") //
                    .end("<h1>Hey! This is my first Vert.x application!</h1>"); //
        });

        router.get("/api/articles").handler(this::getAll);
        router.get("/api/articles/:id").handler(this::getOne);
        router.route("/api/articles*").handler(BodyHandler.create());
        router.post("/api/articles").handler(this::addOne);
        router.delete("/api/articles/:id").handler(this::deleteOne);
        router.put("/api/articles/:id").handler(this::updateOne);

        ConfigRetriever configRetriever = ConfigRetriever.create(vertx);

        // Start sequence:
        // 1 - Retrieve the configuration
        //      |- 2 - Create the JDBC client
        //      |- 3 - Connect to the database (retrieve a connection)
        //              |- 4 - Create table if needed
        //                   |- 5 - Add some data if needed
        //                          |- 6 - Close connection when done
        //              |- 7 - Start HTTP server
        //      |- 9 - we are done!

        ConfigRetriever.getConfigAsFuture(configRetriever)
                .compose(config -> {
                    jdbc = JDBCClient.createShared(vertx, config, "articles");

                    return connect() //
                            .compose(connection -> { //
                                Future<Void> future = Future.future();
                                createTableIfNeeded(connection) //
                                        .compose(this::createSomeDataIfNone) //
                                        .setHandler(x -> {
                                            connection.close();
                                            future.handle(x.mapEmpty());
                                        });
                                return future;
                            })
                            .compose(v -> createHttpServer());

                })
                .setHandler(fut);
    }

    private Future<Void> createHttpServer() {
        //Create the HTTP server and pass the
        // "accept" method to the request handler
        Future<Void> future = Future.future();
        vertx.createHttpServer() //
                .requestHandler(router::accept) //
                .listen(config().getInteger("HTTP_PORT", 8080), //
                        handler -> future.handle(handler.mapEmpty()));

        return future;
    }

    private Future<SQLConnection> connect() {
        Future future = Future.future();
        jdbc.getConnection(ar -> //
                future.handle(ar.map(connection -> //
                        connection.setOptions(new SQLOptions().setAutoGeneratedKeys(true)))));
        return future;
    }

    private Future<Article> insert(SQLConnection connection, Article article, boolean closeConnection) {
        Future<Article> future = Future.future();
        String sql = "INSERT INTO Articles (title, url) VALUES (?, ?)";
        connection.updateWithParams(sql,
                new JsonArray().add(article.getTitle()).add(article.getUrl()),
                ar -> {
                    if (closeConnection) {
                        connection.close();
                    }
                    future.handle(
                            ar.map(res -> new Article(res.getKeys().getLong(0), article.getTitle(), article.getUrl()))
                    );
                }
        );
        return future;
    }

    private Future<List<Article>> query(SQLConnection connection) {
        Future<List<Article>> future = Future.future();
        connection.query("SELECT * FROM articles", result -> {
                    connection.close();
                    future.handle(
                            result.map(rs -> rs.getRows().stream().map(Article::new).collect(Collectors.toList()))
                    );
                }
        );
        return future;
    }

    private Future<Article> queryOne(SQLConnection connection, String id) {
        Future<Article> future = Future.future();
        String sql = "SELECT * FROM articles WHERE id = ?";
        connection.queryWithParams(sql, new JsonArray().add(Integer.valueOf(id)), result -> {
            connection.close();
            future.handle(
                    result.map(rs -> {
                        List<JsonObject> rows = rs.getRows();
                        if (rows.size() == 0) {
                            throw new NoSuchElementException("No article with id " + id);
                        } else {
                            JsonObject row = rows.get(0);
                            return new Article(row);
                        }
                    })
            );
        });
        return future;
    }

    private Future<Void> update(SQLConnection connection, String id, Article article) {
        Future<Void> future = Future.future();
        String sql = "UPDATE articles SET title = ?, url = ? WHERE id = ?";
        connection.updateWithParams(sql, new JsonArray().add(article.getTitle()).add(article.getUrl())
                        .add(Integer.valueOf(id)),
                ar -> {
                    connection.close();
                    if (ar.failed()) {
                        future.fail(ar.cause());
                    } else {
                        UpdateResult ur = ar.result();
                        if (ur.getUpdated() == 0) {
                            future.fail(new NoSuchElementException("No article with id " + id));
                        } else {
                            future.complete();
                        }
                    }
                });
        return future;
    }

    private Future<Void> delete(SQLConnection connection, String id) {
        Future<Void> future = Future.future();
        String sql = "DELETE FROM Articles WHERE id = ?";
        connection.updateWithParams(sql,
                new JsonArray().add(Integer.valueOf(id)),
                ar -> {
                    connection.close();
                    if (ar.failed()) {
                        future.fail(ar.cause());
                    } else {
                        if (ar.result().getUpdated() == 0) {
                            future.fail(new NoSuchElementException("Unknown article " + id));
                        } else {
                            future.complete();
                        }
                    }
                }
        );
        return future;
    }

    private Future<SQLConnection> createTableIfNeeded(SQLConnection connection) {
        Future<SQLConnection> future = Future.future();
        vertx.fileSystem().readFile("tables.sql", ar -> {
            if (ar.failed()) {
                future.fail(ar.cause());
            } else {
                connection.execute(ar.result().toString(),
                        ar2 -> future.handle(ar2.map(connection))
                );
            }
        });
        return future;
    }

    private Future<SQLConnection> createSomeDataIfNone(SQLConnection connection) {
        Future<SQLConnection> future = Future.future();
        connection.query("SELECT * FROM Articles", select -> {
            if (select.failed()) {
                future.fail(select.cause());
            } else {
                if (select.result().getResults().isEmpty()) {
                    Article article1 = new Article("Fallacies of distributed computing",
                            "https://en.wikipedia.org/wiki/Fallacies_of_distributed_computing");
                    Article article2 = new Article("Reactive Manifesto",
                            "https://www.reactivemanifesto.org/");
                    Future<Article> insertion1 = insert(connection, article1, false);
                    Future<Article> insertion2 = insert(connection, article2, false);
                    CompositeFuture.all(insertion1, insertion2)
                            .setHandler(r -> future.handle(r.map(connection)));
                } else {
                    future.complete(connection);
                }
            }
        });
        return future;
    }

    private void getAll(RoutingContext rc) {
        connect()
                .compose(this::query)
                .setHandler(ok(rc));
    }

    private void addOne(RoutingContext rc) {
        Article article = rc.getBodyAsJson().mapTo(Article.class);
        connect()
                .compose(connection -> insert(connection, article, true))
                .setHandler(created(rc));
    }


    private void deleteOne(RoutingContext rc) {
        String id = rc.pathParam("id");
        connect()
                .compose(connection -> delete(connection, id))
                .setHandler(noContent(rc));
    }


    private void getOne(RoutingContext rc) {
        String id = rc.pathParam("id");
        connect()
                .compose(connection -> queryOne(connection, id))
                .setHandler(ok(rc));
    }

    private void updateOne(RoutingContext rc) {
        String id = rc.request().getParam("id");
        Article article = rc.getBodyAsJson().mapTo(Article.class);
        connect()
                .compose(connection -> update(connection, id, article))
                .setHandler(noContent(rc));
    }
}
