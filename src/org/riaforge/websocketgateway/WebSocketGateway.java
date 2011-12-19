package org.riaforge.websocketgateway;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import org.webbitserver.WebServer;
import org.webbitserver.WebSocketConnection;
import org.webbitserver.WebSocketHandler;

import coldfusion.eventgateway.CFEvent;
import coldfusion.eventgateway.Gateway;
import coldfusion.eventgateway.GatewayHelper;
import coldfusion.eventgateway.GatewayServices;
import coldfusion.eventgateway.Logger;
import coldfusion.runtime.Array;

import static org.webbitserver.WebServers.createWebServer;

public class WebSocketGateway implements Gateway, WebSocketHandler {

    // The handle to the CF gateway service
    protected GatewayServices gatewayService;

    // ID provided by EventService
    protected String gatewayID;

    // CFC Listeners for our events
    private ArrayList<String> cfcListeners = new ArrayList<String>();

    // The default function to pass our events to
    protected String cfcEntryPoint = "onIncomingMessage";

    // A collection of connected clients
    private Hashtable<String, WebSocketConnection> connectionRegistry = new Hashtable<String, WebSocketConnection>();

    // The thread that is running the webbit webserver
    protected Thread clientThread;

    // Current status
    protected int status = STOPPED;

    // A logger
    protected Logger log;

    // The webbit webserver
    protected WebServer webServer;

    // The webbit webserver defaults
    public static final int DEFAULT_PORT = 1225;

    // The webbit webserver settings
    protected int port = DEFAULT_PORT; 

    public WebSocketGateway(String id, String configpath) {
        
        gatewayID = id;
        gatewayService = GatewayServices.getGatewayServices();
        // log things to socket-gateway.log in the CF log directory
        log = gatewayService.getLogger("websocket-gateway");        
        
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
        
    }

    /* Gateway */    

    @Override
    public void start() {
        status = STARTING;
        try {
            
            webServer = createWebServer(port)
                .add("/", this)
                .start();    
            
        } catch(Exception e) {
            
            log.error("WebSocketGateway(" + gatewayID
                    + ") Unable to start webbit webserver." 
                    + "': " + e.toString(), e);
            
        }
        status = RUNNING;
    }
    
    @Override
    public void stop() {
        status = STOPPING;
        try {
            
            webServer.stop();
            
        } catch(Exception e) {
            
            log.error("WebSocketGateway(" + gatewayID
                    + ") Unable to stop webbit webserver." 
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
            WebSocketConnection conn = null;
            if (theKey != null) {
                conn = connectionRegistry.get(theKey);
                if (conn != null) {
                    sendTo(conn,message);
                }
                return "OK";
            }
            
            Array keys = (Array) data.get("DESTINATIONWEBSOCKETIDS");
            HashSet<WebSocketConnection> conns = null;
            if(keys != null) {
                conns = new HashSet<WebSocketConnection>(keys.size());
                for (int i = 0; i < keys.size(); i++)
                {
                    WebSocketConnection c = connectionRegistry.get(keys.get(i));
                    if (c != null) {
                        conns.add(c);
                    }
                }
                if (conns != null && conns.size() > 0) { 
                    sendTo(conns,message); 
                }
                return "OK"; 
            } 
            
            sendToAll(message); 
            
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
    
    /* WebSocketHandler */
    
    @Override
    public void onClose(WebSocketConnection conn) throws Exception {
        
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
    public void onMessage(WebSocketConnection conn, String message)
            throws Throwable {
        
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
    public void onMessage(WebSocketConnection conn, byte[] message)
            throws Throwable {
        // TODO Auto-generated method stub
        // TODO Implement binay message handling
    }

    @Override
    public void onOpen(WebSocketConnection conn) throws Exception {
        
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

    @Override
    public void onPong(WebSocketConnection conn, String message) throws Throwable {
        // TODO Auto-generated method stub
    }

    /* Helpers */

    private String getUniqueKey(Object x) {
        Integer z = new Integer(System.identityHashCode(x));
        return z.toString();
    }

    private void sendToAll(String message) {
        for ( WebSocketConnection conn : connectionRegistry.values() ) {
            conn.send(message);
        }        
    }

    private void sendTo(HashSet<WebSocketConnection> conns, String message) {
        for ( WebSocketConnection conn : conns ) {
            conn.send(message);
        }        
    }

    private void sendTo(WebSocketConnection conn, String message) {
        conn.send(message);
    }

}
