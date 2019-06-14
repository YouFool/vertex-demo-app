package org.jlnh;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jlnh.model.Article;

import java.util.*;

/**
 * My first verticle.
 *
 * @author João Heckmann
 */
public class MyFirstVerticle extends AbstractVerticle {

    //Create a router object
    private Router router;

    private Map<Integer, Article> readingList = new LinkedHashMap<>();

    private void createSomeData() {
        Article article1 = new Article("Fallacies of distributed computing","https://en.wikipedia.org/wiki/Fallacies_of_distributed_computing");
        Article article2 = new Article("Reactive Manifesto","https://www.reactivemanifesto.org/");
        readingList.put(article1.getId(), article1);
        readingList.put(article2.getId(), article2);
    }

    // let's deploy the verticle
    @Override
    public void start(Future future) {
        createSomeData();
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
        router.route("/api/articles*").handler(BodyHandler.create());
        router.post("/api/articles").handler(this::addOne);
        router.delete("/api/articles/:id").handler(this::deleteOne);

        ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
        configRetriever.getConfig(config -> {
            if (config.failed()) {
                future.fail(config.cause());
            } else {
                this.deployVerticle(future);
            }
        });
    }

    private void deployVerticle(Future future) {
        //Create the HTTP server and pass the
        // "accept" method to the request handler
        vertx.createHttpServer() //
                .requestHandler(router::accept)
                .listen(config().getInteger("HTTP_PORT", 8080), result -> {
                    if (result.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(result.cause());
                    }
                });
    }

    private void getAll(RoutingContext routingContext) {
        routingContext.response() //
                .putHeader("content-type", "application/json; charset=utf-8") //
                .end(Json.encodePrettily(readingList.values()));
    }

    private void addOne(RoutingContext routingContext) {
        Article article = routingContext.getBodyAsJson().mapTo(Article.class);
        readingList.put(article.getId(), article);
        routingContext.response() //
                .setStatusCode(201) //
                .putHeader("content-type", "application/json; charset=utf-8") //
                .end(Json.encodePrettily(article));
    }

    private void deleteOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        try {
            readingList.remove(Integer.valueOf(id));
            routingContext.response().setStatusCode(204).end();
        } catch (NumberFormatException e) {
            routingContext.response().setStatusCode(400).end("Could not delete article");
        }
    }
}
