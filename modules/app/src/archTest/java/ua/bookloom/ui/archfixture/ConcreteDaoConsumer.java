package ua.bookloom.ui.archfixture;

import ua.bookloom.persistence.archfixture.FixtureProjectDao;

/**
 * Violation fixture for {@code ports-not-concretes}: a {@code :ui} class depending on a concrete DAO in
 * {@code :persistence} rather than on the {@code :api} repository port.
 */
public final class ConcreteDaoConsumer {

    public String describe(FixtureProjectDao dao) {
        return dao.tableName();
    }
}
