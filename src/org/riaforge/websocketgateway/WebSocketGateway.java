package org.riaforge.websocketgateway;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import net.tootallnate.websocket.*;

import coldfusion.eventgateway.CFEvent;
import coldfusion.eventgateway.Gateway;
import coldfusion.eventgateway.GatewayHelper;
import coldfusion.eventgateway.GatewayServices;
import coldfusion.eventgateway.Logger;
import coldfusion.runtime.Array;


public class WebSocketGateway extends WebSocketServer implements Gateway {

    // The handle to the CF gateway service
    protected GatewayServices gatewayService;

    // ID provided by EventService
    protected String gatewayID;

    // CFC Listeners for our events
    private CopyOnWriteArrayList<String> cfcListeners = new CopyOnWriteArrayList<String>();

    // The default function to pass our events to
    protected String cfcEntryPoint = "onIncomingMessage";
    
    // A collection of connected clients
    private Hashtable<String, WebSocket> connectionRegistry = new Hashtable<String, WebSocket>();

    // Out status
    protected int status = STOPPED;

    // A logger
    protected Logger log;

    // default port
    public static final int DEFAULT_PORT = 1225;

    public WebSocketGateway(String id, String configpath) {
        
        gatewayID = id;
        gatewayService = GatewayServices.getGatewayServices();
        // log things to socket-gateway.log in the CF log directory
        log = gatewayService.getLogger("websocket-gateway");        
        
        int port = DEFAULT_PORT;
        
        try {
            FileInputStream pFile = new FileInputStream(configpath);
            Properties p = new Properties();
            p.load(pFile);
            pFile.close();
            if (p.containsKey("port"))
                port = Integer.parseInt(p.getProperty("port"));
         } catch (IOException e) {
            // do nothing. use default value for port.
            log.warn("WebSocketGateway(" + gatewayID
                    + ") Unable to read configuration file '" + configpath
                    + "': " + e.toString(), e);
        }
        
        setPort(port);
        
    }

    /* Gateway */    

    @Override
    public void start() {
        status = STARTING;
        try {
            
            super.start();
            
        } catch(Exception e) {
            
            log.error("WebSocketGateway(" + gatewayID
                    + ") Unable to start websocket webserver." 
                    + "': " + e.toString(), e);
            
        }
        status = RUNNING;
    }
    
    @Override
    public void stop() {
        status = STOPPING;
        try {
            
            super.stop();
            
        } catch(Exception e) {
            
            log.error("WebSocketGateway(" + gatewayID
                    + ") Unable to stop websocket webserver." 
                    + "': " + e.toString(), e);
            
        }
        status = STOPPED;
    }
    
    @Override
    public void restart() {
        stop();
        start();
    }
    
    @Override
    public String outgoingMessage(CFEvent cfmsg) {
        // Get the table of data returned from the even handler
        Map<?, ?> data = cfmsg.getData();
        
        Object value = data.get("MESSAGE");
        
        if (value != null) {
            // Play it safe and convert message to a String
            // TODO: convert value to JSON
            String message = value.toString();
            
            String theKey = (String) data.get("DESTINATIONWEBSOCKETID");
            WebSocket conn = null;
            if (theKey != null) {
                conn = connectionRegistry.get(theKey);
                if (conn != null) {
                    try {
                        sendTo(conn,message);
                        //System.out.println("message sent to one client");
                    } catch (IOException e) {
                        return e.getMessage();
                    }
                }
                return "OK";
            }
            
            Array keys = (Array) data.get("DESTINATIONWEBSOCKETIDS");
            HashSet<WebSocket> conns = null;
            if(keys != null) {
                conns = new HashSet<WebSocket>(keys.size());
                for (int i = 0; i < keys.size(); i++)
                {
                    WebSocket c = connectionRegistry.get(keys.get(i));
                    if (c != null) {
                        conns.add(c);
                    }
                }
                
                if (conns != null && conns.size() > 0) {
                    try {
                        sendTo(conns,message);
                    } catch (IOException e) {
                        return e.getMessage();
                    }
                }
                return "OK";
            }
            
            try {
                sendToAll(message);
            } catch (IOException e) {
                return e.getMessage();
            }
            
        }
        
        // Return a String, possibly a messageID number or error string.
        return "OK";
    }
    
    @Override
    public String getGatewayID() {
        return gatewayID;
    }
    
    @Override
    public GatewayHelper getHelper() {
        return null;
    }
    
    @Override
    public int getStatus() {
        return status;
    }
    
    @Override
    public void setCFCListeners(String[] listeners) {
        for (int i = 0; i < listeners.length; i++) {
            cfcListeners.add(listeners[i]);
        }
    }
    
    @Override
    public void setGatewayID(String id) {
        gatewayID = id;
    }
    
    /* WebSocketServer */
    
    @Override
    public void onClientClose(WebSocket conn) {
        
        // Get a key for the connection
        String theKey = getUniqueKey(conn);
        connectionRegistry.remove(theKey);
        
        for (String path : cfcListeners) {
            
            CFEvent event = new CFEvent(gatewayID);
            
            Hashtable<String, Object> mydata = new Hashtable<String, Object>();
            mydata.put("CONN", conn);
            
            event.setData(mydata);
            event.setGatewayType("WebSocket");
            event.setOriginatorID(theKey);
            event.setCfcMethod("onClose");
            event.setCfcTimeOut(10);
            if (path != null) {
                event.setCfcPath(path);
            }
            
            boolean sent = gatewayService.addEvent(event);
            if (!sent) {
                log
                .error("SocketGateway("
                        + gatewayID
                        + ") Unable to put message on event queue. Message not sent from "
                        + gatewayID + ", thread " + theKey + ".");
            }
        }
        
    }

    @Override
    public void onClientMessage(WebSocket conn, String message) {
       
       // Get a key for the connection
        String theKey = getUniqueKey(conn);
        
        for (String path : cfcListeners) {
            
            CFEvent event = new CFEvent(gatewayID);
            
            Hashtable<String, Object> mydata = new Hashtable<String, Object>();
            mydata.put("MESSAGE", message);
            mydata.put("CONN", conn);
            
            event.setData(mydata);
            event.setGatewayType("WebSocket");
            event.setOriginatorID(theKey);
            event.setCfcMethod(cfcEntryPoint);
            event.setCfcTimeOut(10);
            if (path != null) {
                event.setCfcPath(path);
            }
            
            boolean sent = gatewayService.addEvent(event);
            if (!sent) {
                log
                .error("SocketGateway("
                        + gatewayID
                        + ") Unable to put message on event queue. Message not sent from "
                        + gatewayID + ", thread " + theKey
                        + ".  Message was " + message);
            }
        }
        
    }

    @Override
    public void onClientOpen(WebSocket conn) {
        
        // Get a key for the connection
        String theKey = getUniqueKey(conn);
        connectionRegistry.put(theKey, conn);
        
        for (String path : cfcListeners) {
            
            CFEvent event = new CFEvent(gatewayID);
            
            Hashtable<String, Object> mydata = new Hashtable<String, Object>();
            mydata.put("CONN", conn);
            
            event.setData(mydata);
            event.setGatewayType("WebSocket");
            event.setOriginatorID(theKey);
            event.setCfcMethod("onOpen");
            event.setCfcTimeOut(10);
            if (path != null) {
                event.setCfcPath(path);
            }
            
            boolean sent = gatewayService.addEvent(event);
            if (!sent) {
                log
                .error("SocketGateway("
                        + gatewayID
                        + ") Unable to put message on event queue. Message not sent from "
                        + gatewayID + ", thread " + theKey + ".");
            }
        }
    }

    /* Helpers */

    private String getUniqueKey(Object x) {
        Integer z = new Integer(System.identityHashCode(x));
        return z.toString();
    }

}
