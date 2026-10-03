package services;

import beans.Consume;
import beans.House;
import beans.Houses;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("houses")
public class HouseServices {

    //Permette di inserire una nuova casa
    @Path("/add_casa")
    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public Response addHouse(House h){
        List<House> list = Houses.getInstance().addHouse(h);
        if (list == null)
            return Response.status(Response.Status.NOT_ACCEPTABLE).build();

        GenericEntity<List<House>> genericEntity = new GenericEntity<List<House>>(list) {};
        return Response.ok(genericEntity, MediaType.APPLICATION_JSON).build();
    }

    //Permette di eliminare una casa dalla rete
    @Path("/delete_casa")
    @DELETE
    public Response deleteHouse(@QueryParam("id") int id){
        if(Houses.getInstance().deleteHouse(id))
            return Response.ok().build();

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    //Permette di inserire un nuovo consumo complessivo o singolo
    @Path("/add_consumo")
    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    public Response addConsume(Consume c) {
        if (c == null)
            return Response.status(Response.Status.BAD_REQUEST).build();

        if (c.getHouseId() == 0 || Houses.getInstance().checkHouse(c.getHouseId())) {
            Houses.getInstance().addConsume(c);
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_ACCEPTABLE).build();
    }
}
