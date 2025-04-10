package ddvote.client;
import ddvote.naming.NamingService;
import ddvote.server.NodeService;
import ddvote.shared.*;
import javax.swing.*;
import java.rmi.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.Date;
// Client Logic (Controller)
public class VotingClient {
    private static final Logger LOGGER = Logger.getLogger(VotingClient.class.getName());
    private final ClientGUI gui; private final ExecutorService executor;
    private String namingHost; private NamingService namingStub; private NodeService primaryStub;
    private String primaryId; private volatile boolean connected = false; private String loggedInVoterId = null;

    public VotingClient(ClientGUI gui) { this.gui = gui; this.executor = Executors.newSingleThreadExecutor(); setupLogger(); }
    public void connect(String host) { if (connected) return; this.namingHost = host.trim(); gui.setConnectEnabled(false);
        executor.submit(() -> { try { String url = "rmi://" + namingHost + ":" + RemoteObjectUtils.RMI_REGISTRY_PORT + "/" + NamingService.LOOKUP_NAME;
            namingStub = RemoteObjectUtils.lookupObject(url); logAndUpdate("Connected Naming Service."); if (findPrimary()) { connected = true;
                logAndUpdate("Connected Primary: " + primaryId); fetchInitialState(); } else { logAndUpdate("ERROR: Primary not found."); disconnect(); }
        } catch (Exception e) { logAndUpdate("ERROR: Connect failed: " + e.getMessage()); disconnect(); } finally { SwingUtilities.invokeLater(() -> gui.setConnectEnabled(true)); } }); }
    private boolean findPrimary() { if (namingStub == null) return false; try { Map<String, String> services = namingStub.listServices();
        for (Map.Entry<String, String> e : services.entrySet()) { if (e.getKey().startsWith(NodeService.SERVICE_NAME_PREFIX)) { String id = e.getKey().substring(NodeService.SERVICE_NAME_PREFIX.length());
            try { NodeService p = RemoteObjectUtils.lookupObject(e.getValue()); p.getElectionState(); /* Ping */ primaryStub = p; primaryId = id; return true; } catch (Exception ex) { continue; } } }
    } catch (RemoteException e) { LOGGER.warning("List services failed"); } primaryStub = null; primaryId = null; return false; }
    private void fetchInitialState() { execute(stub -> { ElectionState es = stub.getElectionState(); List<Candidate> cs = stub.getCandidates();
        SwingUtilities.invokeLater(() -> { gui.updateElectionStatus(es); gui.displayCandidates(cs); logAndUpdate("Election: "+es); }); return null; }, "fetch state"); }
    public void disconnect() { connected = false; namingStub = null; primaryStub = null; primaryId = null; loggedInVoterId = null;
        SwingUtilities.invokeLater(() -> { gui.updateConnectionStatus(false, "Disconnected"); gui.updateLoginStatus(false, null); gui.clearAll(); }); }
    // --- RMI Call Wrapper ---
    private <T> T execute(RemoteOperation<T> op, String desc) { if (primaryStub == null) { logAndUpdate("ERROR: Not connected."); return null; }
        try { return op.execute(primaryStub); } catch (ConnectException | NoSuchObjectException ce) { handleReconnect(desc, ce); return null; }
        catch (RemoteException re) { logAndUpdate("ERROR ("+desc+"): "+re.getMessage()); return null; } catch (Exception e) { logAndUpdate("UNEXPECTED ERROR ("+desc+"): "+e.getMessage()); return null;} }
    private void handleReconnect(String opDesc, Exception e) { logAndUpdate("ERROR ("+opDesc+"): Connection lost: " + e.getMessage()); primaryStub = null; primaryId = null;
        SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(false, "Connection Lost")); executor.submit(() -> { logAndUpdate("Attempting reconnect...");
            if (findPrimary()) { connected = true; logAndUpdate("Reconnected Primary: " + primaryId); SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(true, "Reconnected: " + primaryId));
                fetchInitialState(); if (loggedInVoterId != null) { logAndUpdate("Please login again."); loggedInVoterId = null; SwingUtilities.invokeLater(() -> gui.updateLoginStatus(false, null)); }
            } else { logAndUpdate("Reconnect failed."); disconnect(); } }); }
    @FunctionalInterface interface RemoteOperation<T> { T execute(NodeService stub) throws Exception; }
    // --- Requests ---
    public void reqRegister(String id, String pw) { execute(stub -> { boolean ok = stub.registerVoter(new Credentials(id, pw)); SwingUtilities.invokeLater(() -> {
        if(ok) gui.showInfo("Registered."); else gui.showError("Register Failed (Exists?)."); }); return null; }, "register"); }
    public void reqLogin(String id, String pw) { execute(stub -> { String vId = stub.loginVoter(new Credentials(id, pw)); loggedInVoterId = vId;
        SwingUtilities.invokeLater(() -> { gui.updateLoginStatus(vId != null, vId); if(vId != null) fetchInitialState(); else gui.showError("Login Failed."); }); return null; }, "login"); }
    public void reqCandidates() { execute(stub -> { List<Candidate> cs = stub.getCandidates(); SwingUtilities.invokeLater(() -> gui.displayCandidates(cs)); return null; }, "get candidates"); }
    public void reqResults() { execute(stub -> { List<VoteResult> rs = stub.getResults(); SwingUtilities.invokeLater(() -> gui.displayResults(rs)); return null; }, "get results"); }
    public void submitVote(String cId) { if (loggedInVoterId == null) { gui.showError("Not logged in."); return; } String vId = loggedInVoterId;
        execute(stub -> { NodeService.VoteResultStatus st = stub.submitVote(vId, cId); SwingUtilities.invokeLater(() -> gui.handleVoteResponse(st)); return null; }, "submit vote"); }
    // --- Util ---
    private void logAndUpdate(String msg) { LOGGER.info(msg); SwingUtilities.invokeLater(() -> gui.appendToLog(msg)); }
    public boolean isConnected() { return connected && primaryStub != null; } public String getLoggedInVoterId() { return loggedInVoterId; }
    private void setupLogger() { /* Basic console logging setup */ Logger root = Logger.getLogger(""); root.setLevel(Level.INFO); Handler h = new ConsoleHandler();
        h.setFormatter(new SimpleFormatter() { @Override public String format(LogRecord r) { return String.format("[%1$tT.%1$tL] [%4$s] %5$s%n", new Date(r.getMillis()), "", "", r.getLevel().getName(), r.getMessage()); }});
        for(Handler rh : root.getHandlers()) root.removeHandler(rh); root.addHandler(h); }
    // --- Main ---
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> { try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        ClientGUI gui = new ClientGUI(); VotingClient client = new VotingClient(gui); gui.setClient(client); gui.setVisible(true); }); }
}