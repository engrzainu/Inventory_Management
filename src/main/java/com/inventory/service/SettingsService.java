package com.inventory.service;

import com.inventory.dto.SettingsRequest;
import com.inventory.model.BusinessSettings;
import com.inventory.repository.BusinessSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.regex.Pattern;

@Service
public class SettingsService {
    private static final Pattern IMAGE_DATA_URL = Pattern.compile("^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$");
    private static final int MAX_LOGO_CHARS = 1_400_000; // about 1 MB of image

    private final BusinessSettingsRepository repo;
    public SettingsService(BusinessSettingsRepository repo) { this.repo = repo; }

    @Transactional
    public BusinessSettings get() {
        return repo.findById(1L).orElseGet(() -> repo.save(new BusinessSettings(1L)));
    }

    @Transactional
    public BusinessSettings update(SettingsRequest r) {
        BusinessSettings s = get();
        s.setBusinessName(r.businessName().trim());
        s.setAddress(r.address() == null ? "" : r.address().trim());
        s.setPhone(r.phone() == null ? "" : r.phone().trim());
        if (r.removeLogo()) {
            s.setLogoData(null);
        } else if (r.logoData() != null && !r.logoData().isBlank()) {
            if (r.logoData().length() > MAX_LOGO_CHARS || !IMAGE_DATA_URL.matcher(r.logoData()).matches()) {
                throw new IllegalArgumentException("The logo must be a PNG, JPG, WEBP or GIF image smaller than 1 MB.");
            }
            s.setLogoData(r.logoData());
        }
        return s;
    }
}
