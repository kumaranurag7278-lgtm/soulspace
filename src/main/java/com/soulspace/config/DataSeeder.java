package com.soulspace.config;

import com.soulspace.model.Doctor;
import com.soulspace.model.DoctorReview;
import com.soulspace.model.SoulBoardPost;
import com.soulspace.repo.Repositories.DoctorRepository;
import com.soulspace.repo.Repositories.DoctorReviewRepository;
import com.soulspace.repo.Repositories.SoulBoardPostRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Sample data for a first run. Clinicians, ratings and reviews here are demo placeholders. */
@Component
public class DataSeeder implements CommandLineRunner {
    private final DoctorRepository doctors;
    private final DoctorReviewRepository reviews;
    private final SoulBoardPostRepository posts;

    public DataSeeder(DoctorRepository doctors, DoctorReviewRepository reviews, SoulBoardPostRepository posts) {
        this.doctors = doctors;
        this.reviews = reviews;
        this.posts = posts;
    }

    @Override
    public void run(String... args) {
        if (doctors.count() == 0) {
            doctor("SS-D100", "Ananya Rao", "Junior Resident Counselor", "Student stress, loneliness, first conversations",
                    2, 100, 4.7, 128, "Warm first step", "AR");
            doctor("SS-D300", "Rohan Mehta", "CBT & Wellness Specialist", "Anxiety, overthinking, sleep and routine",
                    6, 300, 4.8, 342, "Skills you keep", "RM");
            doctor("SS-D500", "Dr. Meera Iyer", "Senior Clinical Psychologist", "Depression, trauma-informed care, relationships",
                    9, 500, 4.9, 517, "Deep clinical work", "MI");
            doctor("SS-D800", "Dr. Vikram Sethi", "Chief Neuro-Psychiatrist & Director", "Complex cases, medication review, second opinions",
                    16, 800, 4.9, 689, "Director's chair", "VS");
            review("SS-D100", "Quiet Lantern", 5, "I was nervous to talk to anyone. Ananya made it feel safe and unhurried.");
            review("SS-D300", "Amber Comet", 5, "The breathing and thought-record tools helped me sleep better within two weeks.");
            review("SS-D500", "Midnight Willow", 5, "She listened first, then gave me a plan that felt like mine. Life-changing.");
            review("SS-D800", "Silver Harbor", 5, "Clear, calm and thorough. I finally understood what was happening and what to do next.");
        }
        if (posts.count() == 0) {
            post("Quiet Moth", "Small win", "Showered, ate breakfast and answered one email today. Slow progress is still progress.", 14, 6, 9, 5);
            post("Soft Ember", "Venting", "Everyone says it gets easier. Some days it just gets quieter and I'm learning to sit in that.", 22, 11, 17, 3);
            post("Lunar Pebble", "Anxiety", "Placement season is eating my head. Anyone else counting days instead of living them?", 31, 19, 12, 1);
        }
    }

    private void doctor(String code, String name, String title, String specialty, int exp, int fee,
                        double rating, int total, String badge, String avatar) {
        Doctor d = new Doctor();
        d.code = code; d.name = name; d.title = title; d.specialty = specialty; d.experience = exp;
        d.fee = fee; d.ratingAverage = rating; d.totalReviews = total; d.badge = badge; d.avatar = avatar;
        d.nextSlot = "";
        doctors.save(d);
    }

    private void review(String code, String author, int rating, String comment) {
        DoctorReview r = new DoctorReview();
        r.doctorCode = code; r.authorAlias = author; r.rating = rating; r.comment = comment;
        r.createdAt = Instant.now().minus(3, ChronoUnit.DAYS);
        reviews.save(r);
    }

    private void post(String alias, String cat, String text, int hugs, int tea, int peace, int hoursAgo) {
        SoulBoardPost p = new SoulBoardPost();
        p.alias = alias; p.category = cat; p.content = text; p.hugsCount = hugs; p.teaCount = tea;
        p.peaceCount = peace; p.createdAt = Instant.now().minus(hoursAgo, ChronoUnit.HOURS);
        posts.save(p);
    }
}
