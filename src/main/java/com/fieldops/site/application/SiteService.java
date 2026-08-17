package com.fieldops.site.application;

import com.fieldops.client.application.ClientService;
import com.fieldops.client.domain.Client;
import com.fieldops.shared.exception.NotFoundException;
import com.fieldops.site.domain.InspectionSite;
import com.fieldops.site.domain.SiteStatus;
import com.fieldops.site.dto.SiteUpsertRequest;
import com.fieldops.site.repository.InspectionSiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SiteService {

    private final InspectionSiteRepository siteRepository;
    private final ClientService clientService;

    public InspectionSite create(UUID clientId, SiteUpsertRequest request) {
        Client client = clientService.getById(clientId);
        InspectionSite site = new InspectionSite();
        site.setClient(client);
        apply(site, request);
        site.setStatus(SiteStatus.ACTIVE);
        return siteRepository.save(site);
    }

    @Transactional(readOnly = true)
    public InspectionSite getById(UUID id) {
        return siteRepository.findById(id).orElseThrow(() -> NotFoundException.of("InspectionSite", id));
    }

    @Transactional(readOnly = true)
    public Page<InspectionSite> listByClient(UUID clientId, SiteStatus status, Pageable pageable) {
        clientService.getById(clientId);
        if (status != null) {
            return siteRepository.findByClientIdAndStatus(clientId, status, pageable);
        }
        return siteRepository.findByClientId(clientId, pageable);
    }

    public InspectionSite update(UUID id, SiteUpsertRequest request) {
        InspectionSite site = getById(id);
        apply(site, request);
        return siteRepository.save(site);
    }

    public InspectionSite updateStatus(UUID id, SiteStatus status) {
        InspectionSite site = getById(id);
        site.setStatus(status);
        return siteRepository.save(site);
    }

    private void apply(InspectionSite site, SiteUpsertRequest request) {
        site.setName(request.name());
        site.setDescription(request.description());
        site.setAddressLine(request.addressLine());
        site.setCity(request.city());
        site.setState(request.state());
        site.setPostalCode(request.postalCode());
        site.setLatitude(request.latitude());
        site.setLongitude(request.longitude());
        site.setContactName(request.contactName());
        site.setContactPhone(request.contactPhone());
    }
}
