package com.soulspace.repo;

import com.soulspace.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class Repositories {
    private Repositories() {}

    public interface AccountRepository extends JpaRepository<Account, Long> {
        Optional<Account> findByEmailIgnoreCase(String email);
        boolean existsByEmailIgnoreCase(String email);
        boolean existsByAliasIgnoreCase(String alias);
    }

    public interface DoctorRepository extends JpaRepository<Doctor, Long> {
        Optional<Doctor> findByCode(String code);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select d from Doctor d where d.code = :code")
        Optional<Doctor> findByCodeForUpdate(@Param("code") String code);
        List<Doctor> findAllByOrderByFeeAsc();
    }

    public interface DoctorReviewRepository extends JpaRepository<DoctorReview, Long> {
        Optional<DoctorReview> findFirstByDoctorCodeOrderByCreatedAtDesc(String doctorCode);
    }

    public interface BookingRepository extends JpaRepository<ConsultationBooking, Long> {
        Optional<ConsultationBooking> findByAppointmentCode(String code);
        List<ConsultationBooking> findByPatientAliasOrderBySlotAsc(String alias);
        boolean existsByAppointmentCode(String code);
        boolean existsByPatientAliasAndDoctorCodeAndStatusNot(String alias, String doctorCode, String status);
        List<ConsultationBooking> findByDoctorCodeAndSlotBetween(String doctorCode, Instant from, Instant to);
    }

    public interface PremiumSubscriptionRepository extends JpaRepository<PremiumSubscription, Long> {
        boolean existsBySubscriptionCode(String code);
        Optional<PremiumSubscription> findFirstByUserEmailIgnoreCaseAndActiveTrueOrderByExpiresAtDesc(String email);
    }

    public interface SoulBoardPostRepository extends JpaRepository<SoulBoardPost, Long> {
        List<SoulBoardPost> findTop50ByOrderByCreatedAtDesc();

        @Modifying @Query("update SoulBoardPost p set p.hugsCount = p.hugsCount + 1 where p.id = :id")
        int addHug(@Param("id") Long id);

        @Modifying @Query("update SoulBoardPost p set p.teaCount = p.teaCount + 1 where p.id = :id")
        int addTea(@Param("id") Long id);

        @Modifying @Query("update SoulBoardPost p set p.peaceCount = p.peaceCount + 1 where p.id = :id")
        int addPeace(@Param("id") Long id);
    }

    public interface MoodEntryRepository extends JpaRepository<MoodEntry, Long> {
        List<MoodEntry> findTop30ByAccountAliasOrderByEntryDateDesc(String alias);
        Optional<MoodEntry> findByAccountAliasAndEntryDate(String alias, LocalDate date);
    }
}
