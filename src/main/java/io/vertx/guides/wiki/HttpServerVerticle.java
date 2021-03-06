/*
 *  Copyright (c) 2017 Red Hat, Inc. and/or its affiliates.
 *  Copyright (c) 2017 INSA Lyon, CITI Laboratory.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.guides.wiki;

import java.util.List;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import io.vertx.guides.wiki.util.UUId;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
// tag::start[]
public class HttpServerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

  	public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";  // <1>
	public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();
	private String wikiDbQueue = "wikidb.queue";

  @Override
  public void start(Future<Void> startFuture) throws Exception {
	  wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");  // <2>
	   
	  /*Future<String> LoginVerticleDeployment = Future.future();  // <1>
	  Future<String> RegisterVerticleDeployment = Future.future(); 
	  vertx.deployVerticle(new LoginVerticle(), LoginVerticleDeployment.completer());  // <2>
	  vertx.deployVerticle(new RegisterVerticle(), RegisterVerticleDeployment.completer());  // <2>
	  RegisterVerticleDeployment.setHandler(ar -> {
// <7>
	  });*/
//	      if (ar.succeeded()) {
	    	HttpServer server = vertx.createHttpServer();
	    	Router mainRouter = Router.router(vertx);
//			mainRouter.mountSubRouter("/productsAPI",regRouter);
	    	
	    	
	    	mainRouter.get("/registerPage").handler(this::pageRegisterHandler);
	    	mainRouter.get("/").handler(this::indexHandler);
	    	mainRouter.post().handler(BodyHandler.create());
			mainRouter.post("/login").handler(this::loginHandler);
	    	mainRouter.post("/register").handler(this::registerHandler);
	    	mainRouter.post("/userInfo").handler(this::userInfoHandler);
	  		int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);  // <3>
			server
			.requestHandler(mainRouter::accept)
			.listen(portNumber, rs -> {
				if (rs.succeeded()) {
					LOGGER.info("HTTP server running on port " + portNumber);
					startFuture.complete();
					} else {
						LOGGER.error("Could not start a HTTP server", rs.cause());
						startFuture.fail(rs.cause());
					}
			});
		
	
  }

  // (...)
  // end::start[]

  // tag::indexHandler[]
//  private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

  private void indexHandler(RoutingContext context) {

        context.put("title", "Login");
        templateEngine.render(context, "templates", "/login.ftl", ar -> {
          if (ar.succeeded()) {
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(ar.result());
          } else {
            context.fail(ar.cause());
          }
        });
      
  }
  // end::indexHandler[]

  // tag::rest[]
/*  private static final String EMPTY_PAGE_MARKDOWN =
  "# A new page\n" +
    "\n" +
    "Feel-free to write in Markdown!\n";
*/
  private void registerHandler(RoutingContext context) {
	
	    String username = context.request().getParam("username");
	    String password = context.request().getParam("password");
	    String conPass = context.request().getParam("conformPassword");
	    if(!password.equals(conPass) ){
	    	 context.response().end("两次密码不一致");
	    }
		
	    JsonObject request = new JsonObject();
		request.put("username", username);
		request.put("password", password);
		request.put("guid", UUId.getUUID());
		
		DeliveryOptions regOptions = new DeliveryOptions().addHeader("action", "register");
	    vertx.eventBus().send(wikiDbQueue, request, regOptions, reply -> {

	     if (reply.succeeded()) {
	    	 DeliveryOptions loginOptions = new DeliveryOptions().addHeader("action", "login_input");
	 	    vertx.eventBus().send(wikiDbQueue, request, loginOptions, res -> {
	 	    	if(res.succeeded()){
	 	    		 DeliveryOptions infoOptions = new DeliveryOptions().addHeader("action", "saveOrUpdateuserInfo");
	 		 	    vertx.eventBus().send(wikiDbQueue, request, infoOptions, re -> {
	 		 	    	if(re.succeeded()){
	 		 	    		context.put("title", "注册成功");
	 		 		        context.put("username", request.getString("username"));
	 		 		        context.put("timestamp", new Date().toString());

	 		 		        templateEngine.render(context, "templates","/login.ftl", ar -> {
	 		 		          if (ar.succeeded()) {
	 		 		            context.response().putHeader("Content-Type", "text/html");
	 		 		            context.response().end(ar.result());
	 		 		          } else {
	 		 		            context.fail(ar.cause());
	 		 		          }
	 		 		        });
	 		 	    	}
	 		 	    });
	 	    		
	 	    	}
	 	    });
	        

	      } else {
	        context.fail(reply.cause());
	      }
	    });
	  }
  
  private void loginHandler(RoutingContext context) {
		
	    String username = context.request().getParam("username");
	    String password = context.request().getParam("password");
	    JsonObject request = new JsonObject();
		request.put("username", username);
		request.put("password", password);

	    DeliveryOptions options = new DeliveryOptions().addHeader("action", "login");
	    vertx.eventBus().send(wikiDbQueue, request, options, reply -> {

	      if (reply.succeeded()) {
	        JsonObject body = (JsonObject) reply.result().body();
	        List userInfo = body.getJsonArray("user").getList();
	        context.put("title", "success");
	        context.put("username", userInfo.get(0).toString());
	       

	        templateEngine.render(context, "templates","/main.ftl", ar -> {
	          if (ar.succeeded()) { 
	            context.response().putHeader("Content-Type", "text/html");
	            context.response().end(ar.result());
	          } else {
	            context.fail(ar.cause());
	          }
	        });

	      } else {
	        context.fail(reply.cause());
	      }
	    });
	  }
  private void userInfoHandler(RoutingContext context) {
		
	    String username = context.request().getParam("username");
	    String password = context.request().getParam("password");
	    JsonObject request = new JsonObject();
		request.put("username", username);
		request.put("password", password);

	    DeliveryOptions options = new DeliveryOptions().addHeader("action", "login");
	    vertx.eventBus().send(wikiDbQueue, request, options, reply -> {

	      if (reply.succeeded()) {
	        JsonObject body = (JsonObject) reply.result().body();
	       // context.put("user", body.getString("user"));
	        context.put("title", "success");
	        context.put("username", body.getJsonArray("user").getList().get(0).toString());
	        context.put("timestamp", new Date().toString());
	        LOGGER.info("-----------------"+context.get("username"));

	        templateEngine.render(context, "templates","/main.ftl", ar -> {
	          if (ar.succeeded()) {
	            context.response().putHeader("Content-Type", "text/html");
	            context.response().end(ar.result());
	          } else {
	            context.fail(ar.cause());
	          }
	        });

	      } else {
	        context.fail(reply.cause());
	      }
	    });
	  }
  
  private void pageRegisterHandler(RoutingContext context) {

	     //   JsonObject body = (JsonObject) reply.result().body();   // <3>
	        context.put("title", "Register");
	      //  context.put("pages", body.getJsonArray("pages").getList());
	        templateEngine.render(context, "templates", "/register.ftl", ar -> {
	          if (ar.succeeded()) {
	            context.response().putHeader("Content-Type", "text/html");
	            context.response().end(ar.result());
	          } else {
	            context.fail(ar.cause());
	          }
	        });
	      
	  }
  
/*  private void pageRenderingHandler(RoutingContext context) {

    String requestedPage = context.request().getParam("page");
    JsonObject request = new JsonObject().put("page", requestedPage);

    DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-page");
    vertx.eventBus().send(wikiDbQueue, request, options, reply -> {

      if (reply.succeeded()) {
        JsonObject body = (JsonObject) reply.result().body();

        boolean found = body.getBoolean("found");
        String rawContent = body.getString("rawContent", EMPTY_PAGE_MARKDOWN);
        context.put("title", requestedPage);
        context.put("id", body.getInteger("id", -1));
        context.put("newPage", found ? "no" : "yes");
        context.put("rawContent", rawContent);
        context.put("content", Processor.process(rawContent));
        context.put("timestamp", new Date().toString());

        templateEngine.render(context, "templates","/page.ftl", ar -> {
          if (ar.succeeded()) {
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(ar.result());
          } else {
            context.fail(ar.cause());
          }
        });

      } else {
        context.fail(reply.cause());
      }
    });
  }*/

/*  private void pageUpdateHandler(RoutingContext context) {

    String title = context.request().getParam("title");
    JsonObject request = new JsonObject()
      .put("id", context.request().getParam("id"))
      .put("title", title)
      .put("markdown", context.request().getParam("markdown"));

    DeliveryOptions options = new DeliveryOptions();
    if ("yes".equals(context.request().getParam("newPage"))) {
      options.addHeader("action", "create-page");
    } else {
      options.addHeader("action", "save-page");
    }

    vertx.eventBus().send(wikiDbQueue, request, options, reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    });
  }*/

/*  private void pageCreateHandler(RoutingContext context) {
    String pageName = context.request().getParam("name");
    String location = "/wiki/" + pageName;
    if (pageName == null || pageName.isEmpty()) {
      location = "/";
    }
    context.response().setStatusCode(303);
    context.response().putHeader("Location", location);
    context.response().end();
  }*/

/*  private void pageDeletionHandler(RoutingContext context) {
    String id = context.request().getParam("id");
    JsonObject request = new JsonObject().put("id", id);
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "delete-page");
    vertx.eventBus().send(wikiDbQueue, request, options, reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/");
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    });
  }*/
  // end::rest[]
}
