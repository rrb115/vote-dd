package ddvote.shared;
import java.io.Serializable;
import java.util.Objects;
// Represents a voting candidate (Data Model)
public class Candidate implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id; private final String name; private final String description;
    public Candidate(String id, String name, String description) { this.id = id; this.name = name; this.description = description; }
    public String getId() { return id; } public String getName() { return name; } public String getDescription() { return description; }
    @Override public String toString() { return name + (description.isEmpty() ? "" : " (" + description + ")"); }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; return id.equals(((Candidate) o).id); }
    @Override public int hashCode() { return Objects.hash(id); }
}