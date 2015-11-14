/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.rest.internal.resources;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.CookieParam;
//import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
//import javax.ws.rs.QueryParam;
//import javax.ws.rs.CookieParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.Context;
//import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.EntityTag;

import org.apache.commons.lang.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openhab.io.net.actions.Exec;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
//import java.util.List;

//import com.sun.jersey.api.json.JSONWithPadding;

/*************************************************************************
 * 
 * This class extends REST interface with CommandLine Resource  
 * GET/PUT request parameters contain scriptFile name to be executed in operating system command shell
 * and few arguments passed to the script.
 * 
 * I'm using the ExecUtil.executeCommandLineAndWaitResponse(commandLine, timeout) to call predefined
 * scripts located in openhab-runtime folder: /contexts 
 * (Don't know what this folder is for, but it is empty and somehow relates to my purpose. 
 * Correct me someone, if that is not a good choice).
 * 
 * ScriptFile name depends on Request Type & Operating System:
 * 	1) WINDOWS: scriptFile.GET.bat / scriptFile.PUT.bat
 * 	2) LINUX: 	scriptFile.GET.sh / scriptFile.PUT.sh
 * 
 * Original purpose of this resource was to use existing jetty webserver and proxy data requests from sqlite database
 * Few testscripts are added to /contexts folder for reference:
 * GET/PUT request on /rest/context/test/ will produce PLAINTEXT response with request arguments and hints for usage.
 * 
 * @author Ervin Sirk
 * @since 1.8.0
 *****************************************************************************************/

@Path(ContextResource.PATH_CONTEXT)
public class ContextResource {

	private static final Logger logger = LoggerFactory.getLogger(ContextResource.class); 
	
	/** The URI path to this resource */
    public static final String PATH_CONTEXT = "context";

 
/**************************************************************************************
 * 
 * 	GET request on /rest/context/<scriptFile>/<arg1>/<arg2>?que1=<arg3>&que2=<arg4>&type=<arg6>
 * 	will execute scriptFile.GET.bat (Windows) OR scriptFile.GET.sh (*nix)
 * 	located at openhab runtime folder /contexts
 * 
 * 	6 Arguments passed on to the script are (no spaces allowed): 
 * 		a) Login name of user (REMOTE_USER) that sent the request ("0" if SECURITY=OFF)
 * 		b) Path Parameters "arg2" & "arg3" (required)
 * 		c) Values of Query Parameters "que1" & "que2" (optional - "0" if missing)
 * 		d) Value of Query Parameter "type" (optional - htm,html,xml,json - "0" if missing)
 * 
 *	If scriptFile does not exist, then "Not Found" (404) response is generated
 * 
 * 	Results printed to stdout are returned as response BodyText
 *  Response Type is specified by QueryArgument "type" or PLAIN_TEXT if "type" value is unknown.
 *  
 *  If Request Header "If-None-Match" was present, then md5 checksum is calculated from results 
 *  and if values match then "Not Modified" (304) response is generated. 
 *
 ***************************************************************************************/
	@Context UriInfo uriInfo;
    @GET @Path("/{scriptfile: [a-zA-Z_0-9]*}/{arg1: [a-zA-Z_0-9]*}/{arg2: [a-zA-Z_0-9]*}") 
	@Produces({ MediaType.WILDCARD })
    public Response callShellScriptAndSendReturnValue(
		@PathParam("scriptfile") String scriptFile,
		@PathParam("arg1") @DefaultValue("0") String Arg1,
   		@PathParam("arg2") @DefaultValue("0") String Arg2,
		@QueryParam("que1") @DefaultValue("0") String Que1,
		@QueryParam("que2") @DefaultValue("0") String Que2,
		@QueryParam("type") @DefaultValue("0") String Type,
		@CookieParam("uid") @DefaultValue("0") String UID,
		@HeaderParam("If-None-Match") @DefaultValue("0") String INM,
		@HeaderParam("Timeout") @DefaultValue("10000") Integer TimeOut,
		@Context HttpServletRequest request) 
    	{
//logger.info("FYI: ContextResource - HeaderParam 'Timeout':" +TimeOut);
			
			if(Type.equals("")) 
				Type = "0";
			final String responseType = getResponseMediaType(Type);

			String uid = "0";
			if(request.getRemoteUser() != null) 
				uid = request.getRemoteUser();
			else
				uid = UID;

			if(INM.equals("0"))	
				INM = "0";
			
			if(Arg1.equals(""))	
				Arg1 = "0";
			if(Arg2.equals(""))	
				Arg2 = "0";
			
			if(Que1.equals(""))	
				Que1 = "0";
			if(Que2.equals(""))	
				Que2 = "0";
			
			if(TimeOut.equals(""))	
				TimeOut = 10000;
			else
				TimeOut = TimeOut-500;
			
			//file extention according to Op.Sys.
			String ext = "sh";
			if(SystemUtils.IS_OS_WINDOWS)
				ext = "bat";
			
			String md5Hash = null;
			
			if(scriptFile.equals(""))	
				scriptFile = "0";       		
			String fileName = scriptFile+".GET."+ext;
			File f = new File("contexts/"+fileName);
			
			//check if scriptfile exists
			if(f.exists() && !f.isDirectory()) {
    			
				String command = "contexts/"+fileName+" "+uid+" "+Arg1+" "+Arg2+" "+Que1+" "+Que2+" "+Type;
//logger.info("FYI: ContextResource - Command To Execute: "+command);
				String result = Exec.executeCommandLine(command,TimeOut);
			
        		md5Hash = md5(result);
//logger.info("FYI: ContextResource request '{}' md5 hash is: '{}'", uriInfo.getAbsolutePath(), md5Hash);
        		EntityTag resultETag = new EntityTag(md5Hash);

            	if(resultETag.toString().equals(INM))
            		return Response.notModified(resultETag).tag(resultETag).build();
            	else		        		
            		return Response.ok(result,responseType).tag(resultETag).build();
    	    } 
            else 
    	    {
            	logger.info("FYI:  ContextResource request at '{}' was for scriptFile '{}' that does not exist", uriInfo.getPath(), fileName);
    	    	return Response.status(404).build();
    	    }	
		}

   
	/***************************************************************************************
	 * 
	 * PUT request on /rest/context/<scriptFile>/<arg1>/<arg2>
	 *
	 * 		will execute scriptFile.PUT.bat (Windows OS) OR scriptFile.PUT.sh (*NIX OS)
	 * 		located at openhab runtime folder /contexts
	 * 
	 *  	Arguments passed on to the script are (no spaces allowed): 
	 * 			a) Cookie Parameter "uid" value ("0" if missing)
	 * 			b) Path Parameters "arg1" & "arg2" (required)
	 * 			c) PayLoad of Request (in PLAIN TEXT, spaces allowed)
	 * 		
	 * 		Results printed to stdout are returned as PLAIN TEXT in response body.
	 * 
	 *************************************************************************************/
    @PUT @Path("/{scriptfile: [a-zA-Z_0-9]*}/{arg1: [a-zA-Z_0-9]*}/{arg2: [a-zA-Z_0-9]*}")
   	@Consumes({MediaType.TEXT_PLAIN})
    @Produces({MediaType.TEXT_PLAIN})
   	public Response callShellScriptWithDataAndSendReturnValue(
   		@PathParam("scriptfile") String scriptFile,
   		@PathParam("arg1") @DefaultValue("0") String Arg1,
   		@PathParam("arg2") @DefaultValue("0") String Arg2,
		@QueryParam("que1") @DefaultValue("0") String Que1,
		@QueryParam("que2") @DefaultValue("0") String Que2,
		@QueryParam("type") @DefaultValue("0") String Type,
		@CookieParam("uid") @DefaultValue("0") String UID,
		@HeaderParam("If-None-Match") @DefaultValue("0") String INM,
		@HeaderParam("Timeout") @DefaultValue("10000") Integer TimeOut,   		
   		@Context HttpServletRequest request,
   		String value) {
    	
			if(Type.equals("")) 
				Type = "0";
//			final String responseType = getResponseMediaType(Type);
	
			String uid = "0";
			if(request.getRemoteUser() != null) 
				uid = request.getRemoteUser();
			else
				uid = UID;
	
			if(INM.equals("0"))	
				INM = "0";
			
			if(Arg1.equals(""))	
				Arg1 = "0";
			if(Arg2.equals(""))	
				Arg2 = "0";
			
			if(Que1.equals(""))	
				Que1 = "0";
			if(Que2.equals(""))	
				Que2 = "0";
			
			if(TimeOut.equals(""))	
				TimeOut = 10000;
			else
				TimeOut = TimeOut-500;
			
			//file extention according to Op.Sys.
			String ext = "sh";
			if(SystemUtils.IS_OS_WINDOWS)
				ext = "bat";
			
			
			if(scriptFile.equals(""))	
				scriptFile = "0";       		
			String fileName = scriptFile+".PUT."+ext;
			File f = new File("contexts/"+fileName);
			
			//check if scriptfile exists
			if(f.exists() && !f.isDirectory()) 
			{

				String result = Exec.executeCommandLine("contexts/"+fileName+" "+uid+" "+Arg1+" "+Arg2+" "+value, TimeOut);
	    	
				if(result!=null) 
	            {
					if(Type!="0")
						return Response.ok(result).build();
					else
						return Response.status(201).build();
	            }
				else
				{
					logger.info("HTTP PUT request at '{}' for scriptFile '{}' returned null", uriInfo.getPath(), scriptFile);
					return Response.status(404).build();
				}
			}
			else
				return Response.status(404).build();
    	}  

    	
    	private String getResponseMediaType(String typeParam) {

        	if(typeParam.equals("xml")) {
        		return MediaType.APPLICATION_XML;
        	} else if(typeParam.equals("json")) {
        		return MediaType.APPLICATION_JSON;
        	} else if(typeParam.equals("html")) {
        		return MediaType.TEXT_HTML;
        	} else if(typeParam.equals("htm")) {
        		return MediaType.TEXT_HTML;
        	} else
        		return MediaType.TEXT_PLAIN;
    	}
    
	    private String md5(String s) {
	        try {
	            MessageDigest m = MessageDigest.getInstance("MD5");
	            m.update(s.getBytes(), 0, s.length());
	            BigInteger i = new BigInteger(1,m.digest());
	            return String.format("%1$032x", i);         
	        } catch (NoSuchAlgorithmException e) {
	            e.printStackTrace();
	        }
	        return null;
	    }
}
