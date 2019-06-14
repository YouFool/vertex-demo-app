package org.jlnh;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

/**
 * My first verticle.
 *
 * @author João Heckmann
 */
public class MyFirstVerticle extends AbstractVerticle {

    // let's deploy the verticle
    @Override
    public void start(Future future) {

        ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
        configRetriever.getConfig(config -> {
            if (config.failed()) {
                future.fail(config.cause());
            } else {
                vertx.createHttpServer() //
                        .requestHandler(handler -> //
                                handler.response() //
                                        .end("<h1>Hey! This is my first Vert.x application!</h1>")) //
                        .listen(config().getInteger("HTTP_PORT", 8080), result -> {
                            if (result.succeeded()) {
                                future.complete();
                            } else {
                                future.fail(result.cause());
                            }
                        });
            }
        });
    }
}
