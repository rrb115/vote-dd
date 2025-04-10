package ddvote.shared;
import java.io.Serializable;
// Holds results for one candidate (Data Model)
public class VoteResult implements Serializable {
    private static final long serialVersionUID = 3L;
    private final String candidateId; private final String candidateName; private final int voteCount;
    public VoteResult(String id, String name, int count) { this.candidateId = id; this.candidateName = name; this.voteCount = count; }
    public String getCandidateId() { return candidateId; } public String getCandidateName() { return candidateName; } public int getVoteCount() { return voteCount; }
    @Override public String toString() { return candidateName + ": " + voteCount + " votes"; }
}