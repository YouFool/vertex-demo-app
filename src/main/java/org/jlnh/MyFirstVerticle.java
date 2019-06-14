package org.jlnh;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

/**
 * My first verticle.
 * @author João Heckmann
 */
public class MyFirstVerticle extends AbstractVerticle {

    // let's deploys the verticle
    @Override
    public void start(Future future) {
        vertx.createHttpServer() //
                .requestHandler(handler -> //
                        handler.response() //
                                .end("<h1>Hey! This is my first Vert.x application!</h1>")) //
                .listen(8080, result -> {
                    if (result.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(result.cause());
                    }
                });
    }
}
