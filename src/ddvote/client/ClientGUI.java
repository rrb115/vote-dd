package ddvote.client;
import ddvote.server.NodeService; // For VoteResultStatus enum
import ddvote.shared.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// Simple Swing GUI (View)
public class ClientGUI extends JFrame {
    private static final String DEFAULT_NAMING_HOST = "localhost";
    private VotingClient client;
    private JTextField namingHostField, voterIdField; private JPasswordField passwordField;
    private JButton connectButton, disconnectButton, registerButton, loginButton, voteButton, refreshCandidatesButton, refreshResultsButton;
    private JLabel connectionStatusLabel, loginStatusLabel, electionStatusLabel, votedStatusLabel;
    private JPanel candidatesPanel; private ButtonGroup candidateGroup; private JTextArea resultsArea, logArea;
    private Map<String, Candidate> candidateMap = new HashMap<>(); private boolean hasVoted = false; private ElectionState currentElectionState = null;

    public ClientGUI() { super("DD-Vote Client (Simple RMI)"); initializeGUI(); updateGUIState(false); }
    public void setClient(VotingClient c) { this.client = c; }
    private void initializeGUI() { setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); setPreferredSize(new Dimension(800, 650));
        setLayout(new BorderLayout(5, 5)); getRootPane().setBorder(new EmptyBorder(5, 5, 5, 5));
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { handleClose(); } });
        JPanel top = new JPanel(); top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS)); top.add(createConnPanel()); top.add(createAuthPanel());
        JPanel center = new JPanel(new GridLayout(1, 2, 5, 0)); center.add(createVotePanel()); center.add(createResultsPanel());
        add(top, BorderLayout.NORTH); add(center, BorderLayout.CENTER); add(createLogPanel(), BorderLayout.SOUTH); pack(); setLocationRelativeTo(null); }
    private JPanel createConnPanel() { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT)); p.setBorder(BorderFactory.createTitledBorder("Connection"));
        p.add(new JLabel("Naming Host:")); namingHostField = new JTextField(DEFAULT_NAMING_HOST, 12); p.add(namingHostField);
        connectButton = new JButton("Connect"); disconnectButton = new JButton("Disconnect"); p.add(connectButton); p.add(disconnectButton);
        connectionStatusLabel = new JLabel("Disconnected"); connectionStatusLabel.setForeground(Color.RED); p.add(connectionStatusLabel);
        connectButton.addActionListener(e -> { if(client!=null) client.connect(namingHostField.getText()); });
        disconnectButton.addActionListener(e -> { if(client!=null) client.disconnect(); }); return p; }
    private JPanel createAuthPanel() { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT)); p.setBorder(BorderFactory.createTitledBorder("Auth"));
        p.add(new JLabel("ID:")); voterIdField = new JTextField(8); p.add(voterIdField); p.add(new JLabel("PW:")); passwordField = new JPasswordField(8); p.add(passwordField);
        registerButton = new JButton("Reg"); loginButton = new JButton("Login"); p.add(registerButton); p.add(loginButton);
        loginStatusLabel = new JLabel("Not logged in"); p.add(loginStatusLabel);
        registerButton.addActionListener(e -> { if(client!=null) client.reqRegister(voterIdField.getText(), new String(passwordField.getPassword())); passwordField.setText(""); });
        loginButton.addActionListener(e -> { if(client!=null) client.reqLogin(voterIdField.getText(), new String(passwordField.getPassword())); passwordField.setText(""); }); return p; }
    private JPanel createVotePanel() { JPanel p = new JPanel(new BorderLayout(5, 5)); p.setBorder(BorderFactory.createTitledBorder("Vote"));
        JPanel status = new JPanel(new BorderLayout()); electionStatusLabel = new JLabel("Election: Unknown"); votedStatusLabel = new JLabel(); status.add(electionStatusLabel, BorderLayout.WEST); status.add(votedStatusLabel, BorderLayout.EAST); p.add(status, BorderLayout.NORTH);
        candidatesPanel = new JPanel(); candidatesPanel.setLayout(new BoxLayout(candidatesPanel, BoxLayout.Y_AXIS)); candidatesPanel.add(new JLabel("(Connect...)"));
        candidateGroup = new ButtonGroup(); JScrollPane sp = new JScrollPane(candidatesPanel); p.add(sp, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER)); refreshCandidatesButton = new JButton("Refresh Cands"); voteButton = new JButton("Vote"); btns.add(refreshCandidatesButton); btns.add(voteButton); p.add(btns, BorderLayout.SOUTH);
        refreshCandidatesButton.addActionListener(e -> { if(client!=null) client.reqCandidates(); });
        voteButton.addActionListener(e -> handleVote()); return p; }
    private JPanel createResultsPanel() { JPanel p = new JPanel(new BorderLayout(5, 5)); p.setBorder(BorderFactory.createTitledBorder("Results"));
        resultsArea = new JTextArea(10, 25); resultsArea.setEditable(false); resultsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); JScrollPane sp = new JScrollPane(resultsArea); p.add(sp, BorderLayout.CENTER);
        refreshResultsButton = new JButton("Refresh Res"); JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER)); bp.add(refreshResultsButton); p.add(bp, BorderLayout.SOUTH);
        refreshResultsButton.addActionListener(e -> { if(client!=null) client.reqResults(); }); return p; }
    private JPanel createLogPanel() { JPanel p = new JPanel(new BorderLayout()); p.setBorder(BorderFactory.createTitledBorder("Log"));
        logArea = new JTextArea(6, 50); logArea.setEditable(false); logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10)); ((DefaultCaret)logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane sp = new JScrollPane(logArea); p.add(sp, BorderLayout.CENTER); return p; }
    // --- Action Handlers & Updates ---
    private void handleVote() { if (!client.isConnected() || client.getLoggedInVoterId() == null) { showError("Not connected/logged in."); return; }
        if (currentElectionState != ElectionState.RUNNING) { showError("Voting not active."); return; } if (hasVoted) { showError("Already voted."); return; }
        ButtonModel sel = candidateGroup.getSelection(); if (sel == null) { showError("Select a candidate."); return; } String cId = sel.getActionCommand();
        if (JOptionPane.showConfirmDialog(this, "Vote for " + candidateMap.get(cId).getName() + "?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            voteButton.setEnabled(false); client.submitVote(cId); } }
    private void handleClose() { if (client != null && client.isConnected()) { if (JOptionPane.showConfirmDialog(this, "Disconnect & Close?", "Confirm Exit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
        client.disconnect(); dispose(); System.exit(0); } } else { dispose(); System.exit(0); } }
    void updateGUIState(boolean connected) { namingHostField.setEnabled(!connected); connectButton.setEnabled(!connected); disconnectButton.setEnabled(connected);
        boolean authPossible = connected && (client == null || client.getLoggedInVoterId() == null); voterIdField.setEnabled(authPossible); passwordField.setEnabled(authPossible);
        registerButton.setEnabled(authPossible); loginButton.setEnabled(authPossible); refreshCandidatesButton.setEnabled(connected); refreshResultsButton.setEnabled(connected);
        if (!connected) { updateLoginStatus(false, null); updateElectionStatus(null); clearAll(); } updateVoteButtonState(); }
    void setConnectEnabled(boolean en) { SwingUtilities.invokeLater(() -> connectButton.setEnabled(en)); }
    void updateConnectionStatus(boolean conn, String msg) { connectionStatusLabel.setText(conn ? "Connected" : msg); connectionStatusLabel.setForeground(conn ? Color.BLUE : Color.RED); updateGUIState(conn); }
    void updateLoginStatus(boolean loggedIn, String voterId) { loginStatusLabel.setText(loggedIn ? "Logged in: " + voterId : "Not logged in"); if (!loggedIn) { clearAll(); hasVoted = false; } updateGUIState(client.isConnected()); }
    void displayCandidates(List<Candidate> candidates) { candidatesPanel.removeAll(); candidateGroup = new ButtonGroup(); candidateMap.clear(); if (candidates == null || candidates.isEmpty()) { candidatesPanel.add(new JLabel("No candidates.")); }
    else { candidates.forEach(c -> { JRadioButton rb = new JRadioButton("<html><b>" + c.getName() + "</b><br><i>" + c.getDescription() + "</i></html>"); rb.setActionCommand(c.getId()); candidateGroup.add(rb); candidatesPanel.add(rb); candidateMap.put(c.getId(), c); }); }
        candidatesPanel.revalidate(); candidatesPanel.repaint(); updateVoteButtonState(); }
    void displayResults(List<VoteResult> results) { resultsArea.setText(""); if (results == null || results.isEmpty()) { resultsArea.append("No results."); }
    else { results.forEach(r -> resultsArea.append(String.format(" %-15s: %d\n", r.getCandidateName(), r.getVoteCount()))); } resultsArea.setCaretPosition(0); }
    void updateElectionStatus(ElectionState state) { currentElectionState = state; electionStatusLabel.setText("Election: " + (state != null ? state.getDisplayName() : "Unknown")); updateVoteButtonState(); }
    void updateVotingStatus(boolean voted) { hasVoted = voted; votedStatusLabel.setText(voted ? "(Voted)" : ""); updateVoteButtonState(); }
    void updateVoteButtonState() { boolean enable = client.isConnected() && client.getLoggedInVoterId() != null && currentElectionState == ElectionState.RUNNING && !hasVoted && !candidateMap.isEmpty();
        voteButton.setEnabled(enable); }
    void handleVoteResponse(NodeService.VoteResultStatus status) { switch (status) { case ACCEPTED: showInfo("Vote Accepted."); updateVotingStatus(true); break;
        case REJECTED_ALREADY_VOTED: showWarning("Already Voted."); updateVotingStatus(true); break; case REJECTED_LOCK_BUSY: showError("Server busy, please try again."); break;
        default: showError("Vote Rejected: " + status); break; } updateVoteButtonState(); } // Re-enable if needed
    void clearAll() { clearCandidates(); clearResults(); updateVotingStatus(false); }
    void clearCandidates() { candidatesPanel.removeAll(); candidatesPanel.add(new JLabel("(Connect...)")); candidateMap.clear(); candidatesPanel.revalidate(); candidatesPanel.repaint(); }
    void clearResults() { resultsArea.setText(""); }
    void appendToLog(String text) { if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> appendToLog(text)); return; } logArea.append(text + "\n"); }
    void showInfo(String msg) { JOptionPane.showMessageDialog(this, msg, "Info", JOptionPane.INFORMATION_MESSAGE); }
    void showError(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); }
    void showWarning(String msg) { JOptionPane.showMessageDialog(this, msg, "Warning", JOptionPane.WARNING_MESSAGE); }
}