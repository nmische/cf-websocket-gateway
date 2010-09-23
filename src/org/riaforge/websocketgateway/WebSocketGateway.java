package org.riaforge.websocketgateway;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import coldfusion.eventgateway.CFEvent;
import coldfusion.eventgateway.Gateway;
import coldfusion.eventgateway.GatewayHelper;
import coldfusion.eventgateway.GatewayServices;
import coldfusion.eventgateway.Logger;
import coldfusion.runtime.Array;

import net.tootallnate.websocket.*;

public class WebSocketGateway extends WebSocketServer implements Gateway {

    // The handle to the CF gateway service
    protected GatewayServices gatewayService = null;

    // ID provided by EventService
    protected String gatewayID = "";

    // CFC Listeners for our events
    private CopyOnWriteArrayList<String> cfcListeners = new CopyOnWriteArrayList<String>();

    // The default function to pass our events to
    protected String cfcEntryPoint = "onIncomingMessage";
    
    // A collection of connected clients
    private Hashtable<String, WebSocket> connectionRegistry = new Hashtable<String, WebSocket>();

    // Out status
    protected int status = STOPPED;

    // default port
    public static final int DEFAULT_PORT = 1225;

    private Logger log;

    /**
     * constructor with config file
     */
    public WebSocketGateway(String id, String configpath) {
        gatewayID = id;
        gatewayService = GatewayServices.getGatewayServices();
        // log things to socket-gateway.log in the CF log directory
        log = gatewayService.getLogger("websocket-gateway");        
        
        int port = DEFAULT_PORT;
        String origin = null;
        String subprotocol = null;
        
        try {
            FileInputStream pFile = new FileInputStream(configpath);
            Properties p = new Properties();
            p.load(pFile);
            pFile.close();
            if (p.containsKey("port"))
                port = Integer.parseInt(p.getProperty("port"));
            if (p.containsKey("origin"))
                origin = p.getProperty("origin");
            if (p.containsKey("subprotocol"))
                subprotocol = p.getProperty("subprotocol");
        } catch (IOException e) {
            // do nothing. use default value for port.
            log.warn("WebSocketGateway(" + gatewayID
                    + ") Unable to read configuration file '" + configpath
                    + "': " + e.toString(), e);
        }
        
        setPort(port);
        setOrigin(origin);
        setSubProtocol(subprotocol);
        
        log.info("WebSocketGateway(" + gatewayID + ") configured on port" + port + ".");

    }

    

    /**
     * Send a message back out of the gateway.
     * <P>
     * The information about the message to send out is found in the Map
     * returned by cfmsg.getData().
     * <P>
     * The values in the Map are <i>Gateway specific</i>. So the component
     * sending the output message will need to know the values the gateway
     * expects to see here.
     * 
     * @param cfmsg
     *            the message to send
     * @return A Gateway specific string, such as an outgoing message ID or
     *         status.
     */
    public String outgoingMessage(coldfusion.eventgateway.CFEvent cfmsg) {
        // Get the table of data returned from the even handler
        Map<?, ?> data = cfmsg.getData();

        // TODO: Your code here

        // You can get named values from this map as set by the CFML CFC code
        // You should not assume String data unless you check.
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
                        //System.out.println("message sent to " + conns.size() + " clients");
                    } catch (IOException e) {
                        return e.getMessage();
                    }                
                }
                return "OK";                
            }            
                        
            try {
                sendToAll(message);
                //System.out.println("message sent to all clients");
            } catch (IOException e) {
                return e.getMessage();
            }            
            
        }

        // Return a String, possibly a messageID number or error string.
        return "OK";
    }

    /**
     * Set the CFClisteners list.
     * <P>
     * Takes a list of fully qualified CF component names (e.g.
     * "my.components.HandleEvent") which should each receive events when the
     * gateway sees one. This will reset the list each time it is called.
     * <P>
     * This is called by the Event Service manager on startup, and may be called
     * if the configuration of the Gateway is changed during operation.
     * 
     * @param listeners
     *            a list of component names
     */
    public void setCFCListeners(String[] listeners) {
        for (int i = 0; i < listeners.length; i++) {
            cfcListeners.add(listeners[i]);
        }
    }

    /**
     * Set the id that uniquely defines the gateway.
     * <P>
     * Generally, you just need to return this string in getGatewayID(). It is
     * used by the event manager to identify the gateway
     * 
     * @param id
     *            this gateways id string
     */
    public void setGatewayID(String id) {
        gatewayID = id;
    }

    /**
     * Return the id that uniquely defines the gateway.
     * 
     * @return the gateway ID set by setGatewayID()
     */
    public String getGatewayID() {
        return gatewayID;
    }

    /**
     * Return a CFC helper class (if any) so that a CFC can invoke Gateway
     * specific utility functions that might be useful to the CFML developer.
     * <P>
     * Called by the CFML function getGatewayHelper(gatewayID).
     * <P>
     * Return null if you do not provide a helper class.
     * 
     * @return an instance of the gateway specific helper class or null
     */
    public GatewayHelper getHelper() {
        // We have no helper class to provide to the CFML programmer
        return null;
    }

    /**
     * Start this Gateway.
     * <P>
     * Perform any gateway specific initialization required. This is where you
     * would start up a listener thread(s) that monitors the event source you
     * are a gateway for.
     * <P>
     * This function <i>should</i> return within an admin configured timeout. If
     * it does not, there is an admin controlled switch which will determine if
     * the thread that calls this function gets killed.
     */
    public void start() {
        status = STARTING;
        super.start();
        status = RUNNING;
        log.info("WebSocketGateway(" + gatewayID + ") started.");
    }

    /**
     * Stop this Gateway.
     * <P>
     * Perform any gateway specific shutdown tasks, such as shutting down
     * listener threads, releasing resources, etc.
     */
    public void stop() {
        status = STOPPING;

        try {
            super.stop();
        } catch (IOException e) {
            // ignore for now
        }

        status = STOPPED;
        
        log.info("WebSocketGateway(" + gatewayID + ") stopped.");
    }

    /**
     * Restart this Gateway
     * <P>
     * Generally this can be implemented as a call to stop() and then start(),
     * but you may be able to optimize this based on what kind of service your
     * gateway talks to.
     */
    public void restart() {
        stop();
        start();
    }

    /**
     * Return the status of the gateway
     * 
     * @return one of STARTING, RUNNING, STOPPING, STOPPED, FAILED.
     */
    public int getStatus() {
        return status;
    }

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
            event.setCfcMethod("onClientClose");
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
            event.setCfcMethod("onClientOpen");
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

    /**
     * returns a unique key value to be used for naming the SocketServerThread
     * and register it in the socketRegistry hashtable
     * 
     * @return String unique key
     */
    private String getUniqueKey(Object x) {
        Integer z = new Integer(System.identityHashCode(x));
        return z.toString();
    }

}
