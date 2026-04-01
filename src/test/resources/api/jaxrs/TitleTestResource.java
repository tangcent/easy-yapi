package com.itangcent.jaxrs;

import com.itangcent.model.UserInfo;
import com.itangcent.model.Result;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;

/**
 * Product Resource APIs
 */
@Path("/title-test")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TitleTestResource {

    /**
     * Get all products
     */
    @GET
    public Result list(@QueryParam("page") int page) {
        return Result.success(new UserInfo());
    }

    /**
     * Get product by ID
     */
    @GET
    @Path("/{id}")
    public Result get(@PathParam("id") Long id) {
        return Result.success(new UserInfo());
    }

    /**
     * Create product
     */
    @POST
    public Result create(UserInfo userInfo) {
        return Result.success(userInfo);
    }

    @GET
    @Path("/no-doc")
    public String noDocMethod() {
        return "ok";
    }
}
