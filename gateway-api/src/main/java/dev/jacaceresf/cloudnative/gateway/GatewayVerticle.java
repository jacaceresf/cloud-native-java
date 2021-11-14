package dev.jacaceresf.cloudnative.gateway;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GatewayVerticle extends AbstractVerticle {


    private static final Logger LOG = LoggerFactory.getLogger(GatewayVerticle.class);

    private WebClient catalog;
    private WebClient inventory;

    @Override
    public void start() throws Exception {

        Router router = Router.router(vertx);
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET));

        router.get("/*").handler(StaticHandler.create("assets"));
        router.get("/health").handler(this::health);
        router.get("/api/products").handler(this::products);

        ConfigRetriever retriever = ConfigRetriever.create(vertx);
        retriever.getConfig(ar -> {
            if (ar.failed()) {

            } else {
                JsonObject config = ar.result();
                String catalogApiHost = config.getString("COMPONENT_CATALOG_HOST", "localhost");
                Integer catalogApiPort = config.getInteger("COMPONENT_CATALOG_PORT", 9000);
                catalog = WebClient.create(vertx, new WebClientOptions()
                        .setDefaultHost(catalogApiHost)
                        .setDefaultPort(catalogApiPort));
                LOG.info("Catalog service endpoint {}", catalogApiPort);

                String inventoryApiHost = config.getString("COMPONENT_INVENTORY_HOST", "localhost");
                Integer inventoryApiPort = config.getInteger("COMPONENT_INVENTORY_PORT", 8080);
                inventory = WebClient.create(vertx, new WebClientOptions()
                        .setDefaultHost(inventoryApiHost)
                        .setDefaultPort(inventoryApiPort));
                LOG.info("Inventory service endpoint {}", inventoryApiPort);

                vertx.createHttpServer()
                        .requestHandler(router)
                        .listen(Integer.getInteger("http.port", 8090));


            }
        });
    }

    private void products(RoutingContext context) {

        catalog
                .get("/api/catalog")
                .expect(ResponsePredicate.SC_OK)
                .as(BodyCodec.jsonArray())
                .rxSend()
                .map(resp -> {
                    List<JsonObject> listOfProducts = new ArrayList<>();
                    for (Object product : resp.body()) {
                        listOfProducts.add((JsonObject) product);
                    }
                    return listOfProducts;
                })
                .flatMap(products -> Observable.fromIterable(products)
                        .collect(JsonArray::new, JsonArray::add)
                ).subscribe(
                        list -> context.response().end(list.encodePrettily()),
                        error -> context.response().setStatusCode(500).end(new JsonObject().put("error", error.getMessage()).toString())
                );
    }

    private Single<JsonObject> getAvailabilityFromInventory(JsonObject product) {

        String itemId = product.getString("itemId");
        LOG.info("Going to retrieve item availability -> [{}]", itemId);

        // Retrieve the inventory for a given product
        return inventory
                .get("/api/inventory/" + itemId)
                .as(BodyCodec.jsonObject())
                .rxSend()
                .map(resp -> {
                    if (resp.statusCode() != 200) {
                        LOG.info("Inventory error for {}: status code {}",
                                product.getString("itemId"), resp.statusCode());
                        return product.copy();
                    }
                    LOG.info("Status OK. Going to return availability");
                    return product.copy().put("availability",
                            new JsonObject().put("quantity", resp.body().getInteger("quantity")));
                });
    }

    private void health(RoutingContext context) {

    }
}
