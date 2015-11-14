/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.rest.internal.listeners;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.openhab.core.items.Item;
import org.openhab.core.items.GroupItem;
import org.openhab.io.rest.RESTApplication;
import org.openhab.io.rest.internal.resources.ItemResource;
import org.openhab.io.rest.internal.resources.GroupResource;
import org.openhab.io.rest.internal.resources.ResponseTypeHelper;
import org.openhab.ui.items.ItemUIRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the {@link ResourceStateChangeListener} implementation for item REST requests
 * 
 * @author Kai Kreuzer
 * @author Oliver Mazur
 * @since 0.9.0
 */
public class ItemStateChangeListener extends ResourceStateChangeListener {

	static final Logger logger = LoggerFactory.getLogger(ItemStateChangeListener.class);
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Object getResponseObject(HttpServletRequest request) {	
		String pathInfo = request.getPathInfo();

		if(pathInfo.endsWith("/state")) {
			// we need to return the plain value
			if (pathInfo.startsWith("/" + ItemResource.PATH_ITEMS)) {
	        	String[] pathSegments = pathInfo.substring(1).split("/");
	            if(pathSegments.length>=2) {
	            	String itemName = pathSegments[1];
					Item item = ItemResource.getItem(itemName);
					if(item!=null) {
						return item.getState().toString();
					}
	            }
			}
		} else {		
			// we want the full item data (as xml or json(p))
			String responseType = (new ResponseTypeHelper()).getResponseType(request);
			if(responseType!=null) {
				String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+(request.getContextPath().equals("null")?"":request.getContextPath())+ RESTApplication.REST_SERVLET_ALIAS +"/";
				if (pathInfo.startsWith("/" + ItemResource.PATH_ITEMS)) {
		        	String[] pathSegments = pathInfo.substring(1).split("/");
		        	Item item = null;
		            if(pathSegments.length>=2) {
		            	String itemName = pathSegments[1];
						item = ItemResource.getItem(itemName);
		            } else {
		            	item = lastChange;
		            }
		            if(item!=null) {
		            	Object itemBean = ItemResource.createItemBean(item, true, basePath);	    	
		            	return itemBean;
					}
		        }
				else if (pathInfo.startsWith("/" + GroupResource.PATH_GROUPS)) {
					Item item = lastChange; 
		            if(item!=null) {
		            	Object itemBean = GroupResource.createItemBean(item, false, basePath);	    	
		            	return itemBean;
					}
				}
			}
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Object getSingleResponseObject(Item item, HttpServletRequest request) {
		return getResponseObject(request);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Set<String> getRelevantItemNames(String pathInfo) {       
        // check, if it is a request for items 
        if (pathInfo.startsWith("/" + ItemResource.PATH_ITEMS)) {
        	String[] pathSegments = pathInfo.substring(1).split("/");

            if(pathSegments.length>=2) {
            	return Collections.singleton(pathSegments[1]);
            } else if (pathSegments.length == 1) {
            	ItemUIRegistry registry = RESTApplication.getItemUIRegistry();
                if(registry!=null) {
                	final Set<String> set = new HashSet<String>();
                	for (Item item : registry.getItems()) {
                		set.add(item.getName());
                	}
                	return set;
                }
            }
        }
        else if (pathInfo.startsWith("/" + GroupResource.PATH_GROUPS)) {
        	String[] pathSegments = pathInfo.substring(1).split("/");

            if(pathSegments.length>=2) {
            	try {
            		ItemUIRegistry registry = RESTApplication.getItemUIRegistry();
                	if(registry!=null)
                	{
                		Item pathItem = registry.getItem(pathSegments[1].toString());
                       	if(pathItem instanceof GroupItem) {
                       		GroupItem gItem = (GroupItem) pathItem;
                       		final Set<String> set = new HashSet<String>();
                       		for (Item item : gItem.getAllMembers()) {
                       			set.add(item.getName());
                       		}
                       		return set;
                       	}
                       	else {
                       		return Collections.singleton(pathSegments[1]);
                       	}
                	}
				} catch (Exception e) {
					return Collections.singleton(pathSegments[1]);
				}
            } else if (pathSegments.length == 1) {
            	ItemUIRegistry registry = RESTApplication.getItemUIRegistry();
                if(registry!=null) {
                	final Set<String> set = new HashSet<String>();
                	for (Item item : registry.getItems()) {
                		set.add(item.getName());
                	}
                	return set;
                }
            }
        }        
        return new HashSet<String>();
	}	
}
