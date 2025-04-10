package ddvote.shared;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.*;

// Helper for common RMI tasks (Infrastructure/Utility)
public class RemoteObjectUtils {
    private static final Logger LOGGER = Logger.getLogger(RemoteObjectUtils.class.getName());
    public static final int RMI_REGISTRY_PORT = 1099;

    public static boolean bindObject(String name, Remote obj) {
        try {
            getOrCreateRegistry().rebind(name, obj);
            LOGGER.fine("RMI bound: " + name);
            return true;
        } catch (RemoteException e) {
            LOGGER.log(Level.SEVERE, "RMI bind failed: " + name, e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Remote> T lookupObject(String name) throws RemoteException, NotBoundException {
        return (T) getOrCreateRegistry().lookup(name);
    }

    public static boolean unbindObject(String name) {
        try {
            getOrCreateRegistry().unbind(name);
            LOGGER.fine("RMI unbound: " + name);
            return true;
        } catch (RemoteException | NotBoundException e) {
            LOGGER.log(Level.WARNING, "RMI unbind failed: " + name, e);
            return false;
        }
    }

    /**
     * Gets the RMI registry or creates one if it does not exist.
     */
    public static Registry getOrCreateRegistry() throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry(RMI_REGISTRY_PORT);
            // Trigger a dummy call to ensure the registry is actually alive
            registry.list();
            return registry;
        } catch (RemoteException e) {
            LOGGER.info("RMI registry not found, creating a new one...");
            return LocateRegistry.createRegistry(RMI_REGISTRY_PORT);
        }
    }

    public static void unexportObject(Remote obj) {
        if (obj == null) return;
        try {
            UnicastRemoteObject.unexportObject(obj, true);
            LOGGER.fine("RMI unexported: " + obj.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to unexport RMI object", e);
        }
    }
}
