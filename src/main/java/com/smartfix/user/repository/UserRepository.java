package com.smartfix.user.repository;

import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link User}.
 *
 * <p>This interface is private to the {@code user} module. Other modules reach account
 * data through {@code UserService} only (plan section 7.3), so it is never injected
 * into a controller or into {@code auth} / {@code request}.</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * @param username an <em>already normalized</em> username - see
     *                 {@link User#normalizeUsername(String)}
     */
    Optional<User> findByUsername(String username);

    /** @param username an <em>already normalized</em> username */
    boolean existsByUsername(String username);

    /** The user-management listing: stable, oldest first. */
    List<User> findAllByOrderByIdAsc();

    /**
     * Reads the administrators who could currently sign in, taking a write lock on
     * every matching row.
     *
     * <p>This exists for one reason: the "at least one active administrator" invariant
     * cannot be enforced by reading a count and then writing. Two administrators
     * demoting each other concurrently would both observe two actives and both
     * proceed, leaving none. Locking the whole set forces those transactions to
     * serialize, so the second one sees the first one's committed result and is
     * correctly refused.</p>
     *
     * <p>All callers take this one lock and only this one, in this order. That single
     * lock point is what keeps the design deadlock-free: no caller locks a target row
     * first and this set second.</p>
     *
     * <p>Must be called inside a write transaction; outside one the lock is released
     * immediately and the guarantee is void.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.accountStatus = :accountStatus order by u.id asc")
    List<User> findForUpdateByRoleAndAccountStatus(@Param("role") Role role,
                                                   @Param("accountStatus") AccountStatus accountStatus);
}
