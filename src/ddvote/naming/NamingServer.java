package ddvote.naming;

import ddvote.shared.RemoteObjectUtils;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

// Standalone Naming Server implementation (Service Implementation)
public class NamingServer extends UnicastRemoteObject implements NamingService {
    private static final Logger LOGGER = Logger.getLogger(NamingServer.class.getName());
    private final ConcurrentHashMap<String, String> registry = new ConcurrentHashMap<>();

    protected NamingServer() throws RemoteException {
        super();
    }

    @Override
    public synchronized void register(String name, String url) {
        LOGGER.info("Registering: " + name + " -> " + url);
        registry.put(name, url);
    }

    @Override
    public synchronized void unregister(String name) {
        LOGGER.info("Unregistering: " + name);
        registry.remove(name);
    }

    @Override
    public synchronized String lookup(String name) {
        return registry.get(name);
    }

    @Override
    public synchronized Map<String, String> listServices() {
        return new ConcurrentHashMap<>(registry); // Return copy to avoid external mutation
    }

    public static void main(String[] args) {
        try {
            // Ensure the RMI registry is running
            RemoteObjectUtils.getOrCreateRegistry();

            // Start Naming Service
            NamingServer server = new NamingServer();
            boolean bound = RemoteObjectUtils.bindObject(NamingService.LOOKUP_NAME, server);

            if (!bound) {
                LOGGER.severe("Failed to bind NamingService. Exiting.");
                System.exit(1);
            }

            LOGGER.info("Naming Service Ready (Port " + RemoteObjectUtils.RMI_REGISTRY_PORT +
                    ", Name: " + NamingService.LOOKUP_NAME + ")");

            // Keep the server running
            Thread.currentThread().join();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Naming Service failed to start", e);
            System.exit(1);
        }
    }
}
