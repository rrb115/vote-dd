import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;

/**
 * A simple program to start the RMI registry programmatically,
 * ensuring the correct hostname property is set.
 */
public class StartRegistry {

    public static void main(String[] args) {
        int port = 1099; // Default RMI port

        try {
            // Set the crucial hostname property BEFORE creating the registry
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");
            System.out.println("System property java.rmi.server.hostname set to 127.0.0.1");

            // Attempt to create the registry on the specified port
            LocateRegistry.createRegistry(port);

            System.out.println("RMI Registry started programmatically on port " + port);
            System.out.println("Registry running. Keep this process alive (Ctrl+C to stop)...");

            // Keep the main thread alive so the registry doesn't exit immediately
            Thread.currentThread().join();

        } catch (RemoteException e) {
            // This likely means a registry is already running on this port
            System.err.println("Failed to create RMI Registry on port " + port + ".");
            System.err.println("It might already be running.");
            System.err.println("Error message: " + e.getMessage());
            // e.printStackTrace(); // Uncomment for full stack trace if needed
            // Exit if we couldn't create it - maybe let the user know to check?
            System.exit(1);
        } catch (InterruptedException e) {
            System.err.println("Registry starter thread interrupted. Exiting.");
            Thread.currentThread().interrupt(); // Preserve interrupt status
        } catch (Exception e) {
            // Catch any other unexpected errors during startup
            System.err.println("An unexpected error occurred while starting the registry:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}