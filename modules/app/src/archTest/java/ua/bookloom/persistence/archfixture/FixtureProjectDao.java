package ua.bookloom.persistence.archfixture;

/**
 * The forbidden target for {@code ports-not-concretes}: a concrete {@code ..Dao} inside {@code :persistence}. A
 * caller in another module must hold the {@code :api} repository interface instead, which is what makes the
 * collaborator mockable and keeps the graph pointing at {@code :api}.
 */
public final class FixtureProjectDao {

    public String tableName() {
        return "project";
    }
}
