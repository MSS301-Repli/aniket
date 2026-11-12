package com.animallocket.user.service;

import com.animallocket.user.domain.Entitlement;
import com.animallocket.user.repo.EntitlementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class EntitlementService {
    private final EntitlementRepository repository;
    private final int invitesFree;
    private final int invitesPro;
    private final Duration spinFree;
    private final Duration spinPro;

    public EntitlementService(EntitlementRepository repository,
                              @Value("${animal.limits.invites-per-day.free:20}") int invitesFree,
                              @Value("${animal.limits.invites-per-day.pro:80}") int invitesPro,
                              @Value("${animal.limits.spin-interval.free:7d}") Duration spinFree,
                              @Value("${animal.limits.spin-interval.pro:1d}") Duration spinPro) {
        this.repository = repository;
        this.invitesFree = invitesFree;
        this.invitesPro = invitesPro;
        this.spinFree = spinFree;
        this.spinPro = spinPro;
    }

    public boolean isPro(String publicId) {
        return repository.findById(publicId).map(Entitlement::isActivePro).orElse(false);
    }

    public String plan(String publicId) {
        return isPro(publicId) ? "PRO" : "FREE";
    }

    public int invitesPerDay(String publicId) {
        return isPro(publicId) ? invitesPro : invitesFree;
    }

    public Duration spinInterval(String publicId) {
        return isPro(publicId) ? spinPro : spinFree;
    }
}
