package cidrz.assessment.backend.auth;

/**
 * The two roles this slice implements. The brief names four user groups; facility administrator and
 * national user are described in A-architecture.md but are not built here, and adding them is a new
 * enum constant plus a branch in {@link AccessPolicy} — the switch there is exhaustive, so the
 * compiler points at every decision that would need revisiting.
 */
public enum Role {

    /** Reads and writes patients at their own facility. */
    CLINICIAN,

    /** Reads across every facility in their district. Cannot write. */
    DISTRICT_OFFICER
}
