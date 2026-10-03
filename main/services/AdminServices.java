package services;

import beans.House;
import beans.Houses;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.OptionalDouble;

@Path("admin")
public class AdminServices {
    //Permette di consultare la lista delle case nella rete
    @Path("/get_house")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHouse(){
        List<House> list = Houses.getInstance().getHouses();
        if(list.isEmpty()){
            return Response.status(Response.Status.NO_CONTENT).build();
        }
        GenericEntity<List<House>> genericEntity = new GenericEntity<List<House>>(list){};
        return Response.ok(genericEntity).build();
    }

    /*
    |---------------------------------------------------------|
    |-----------------INIZIO STATISTICHE CASE-----------------|
    |---------------------------------------------------------|
     */

    //Permette di consultare gli ultimi n consumi del condominio
    @Path("/get_consumi")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getHouseConsumes(@QueryParam("n") int n, @DefaultValue("0")@QueryParam("id") int id){
        String result = Houses.getInstance().getConsumes(n, id);
        if(result.isEmpty())
            return Response.status(Response.Status.NO_CONTENT).build();

        return Response.ok(result).build();
    }

    //Permette di consultare la media degli ultimi n consumi del condominio
    @Path("/get_media")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getHouseAverage(@QueryParam("n") int n, @DefaultValue("0")@QueryParam("id") int id){
        OptionalDouble result = Houses.getInstance().calculateAverage(n, id);
        if (!result.isPresent())
            return Response.status(Response.Status.NO_CONTENT).build();

        return Response.ok(String.valueOf(result.getAsDouble())).build();
    }

    //Permette di consultare la deviazione standard degli ultimi n consumi del condominio
    @Path("/get_std")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getHouseSTD(@QueryParam("n") int n, @DefaultValue("0")@QueryParam("id") int id){
        OptionalDouble result = Houses.getInstance().calculateSTD(n, id);
        if (!result.isPresent())
            return Response.status(Response.Status.NO_CONTENT).build();

        return Response.ok(String.valueOf(result.getAsDouble())).build();
    }
}