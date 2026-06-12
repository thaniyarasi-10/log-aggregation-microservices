package com.kovanlabs.servicemanagementservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.servicemanagementservice.dto.auth.AuthResponse;
import com.kovanlabs.servicemanagementservice.model.AppRole;
import com.kovanlabs.servicemanagementservice.model.AppUser;
import com.kovanlabs.servicemanagementservice.model.Organization;
import com.kovanlabs.servicemanagementservice.model.UserOrganizationMapping;
import com.kovanlabs.servicemanagementservice.model.OrganizationInvite;
import com.kovanlabs.servicemanagementservice.model.OrganizationJoinRequest;
import com.kovanlabs.servicemanagementservice.model.OrganizationApiKey;
import com.kovanlabs.servicemanagementservice.model.UserRoleMapping;
import com.kovanlabs.servicemanagementservice.repository.AppRoleRepository;
import com.kovanlabs.servicemanagementservice.repository.AppUserRepository;
import com.kovanlabs.servicemanagementservice.repository.OrganizationRepository;
import com.kovanlabs.servicemanagementservice.repository.UserOrganizationMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.OrganizationInviteRepository;
import com.kovanlabs.servicemanagementservice.repository.OrganizationJoinRequestRepository;
import com.kovanlabs.servicemanagementservice.repository.OrganizationApiKeyRepository;
import com.kovanlabs.servicemanagementservice.repository.UserRoleMappingRepository;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserOrganizationMappingRepository userOrganizationMappingRepository;
    private final OrganizationInviteRepository organizationInviteRepository;
    private final OrganizationJoinRequestRepository organizationJoinRequestRepository;
    private final OrganizationApiKeyRepository organizationApiKeyRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final AuthService authService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            UserOrganizationMappingRepository userOrganizationMappingRepository,
            OrganizationInviteRepository organizationInviteRepository,
            OrganizationJoinRequestRepository organizationJoinRequestRepository,
            OrganizationApiKeyRepository organizationApiKeyRepository,
            UserRoleMappingRepository userRoleMappingRepository,
            AppUserRepository appUserRepository,
            AppRoleRepository appRoleRepository,
            AuthService authService) {
        this.organizationRepository = organizationRepository;
        this.userOrganizationMappingRepository = userOrganizationMappingRepository;
        this.organizationInviteRepository = organizationInviteRepository;
        this.organizationJoinRequestRepository = organizationJoinRequestRepository;
        this.organizationApiKeyRepository = organizationApiKeyRepository;
        this.userRoleMappingRepository = userRoleMappingRepository;
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.authService = authService;
    }

    @Transactional
    public Organization createOrganization(String name, String type, String userEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized"));

        String orgType = (type == null) ? "PERSONAL" : type.trim().toUpperCase(Locale.ROOT);
        if (!"BUSINESS".equals(orgType) && !"PERSONAL".equals(orgType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid organization type: " + type);
        }

        Organization org = new Organization();
        org.setName(name == null || name.isBlank() ? "New Workspace" : name.trim());
        org.setDomain(null);
        org.setOrganizationType(orgType);
        org.setCreatedAt(LocalDateTime.now());
        org.setUpdatedAt(LocalDateTime.now());
        Organization savedOrg = organizationRepository.save(org);

        UserOrganizationMapping mapping = new UserOrganizationMapping();
        mapping.setUser(user);
        mapping.setOrganization(savedOrg);
        mapping.setStatus("ACTIVE");
        mapping.setAssignedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());
        userOrganizationMappingRepository.save(mapping);

        assignRoleToUserInOrg(user, savedOrg, "ADMIN");

        return savedOrg;
    }

    @Transactional(readOnly = true)
    public AuthResponse switchOrganization(UUID targetOrgId, String userEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized"));

        UserOrganizationMapping mapping = userOrganizationMappingRepository
                .findByUser_IdAndOrganization_IdAndStatus(user.getId(), targetOrgId, "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have an active membership in the target organization"));

        return authService.loginWithEmail(user.getEmail(), user.getUsername());
    }

    @Transactional
    public OrganizationInvite inviteUser(UUID orgId, String email, String invitedByEmail) {
        AppUser inviter = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(invitedByEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inviter not authorized"));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        // Check if inviter is ADMIN in the organization
        boolean isAdmin = userRoleMappingRepository.findByUser_IdAndOrganization_Id(inviter.getId(), org.getId()).stream()
                .anyMatch(rm -> "ADMIN".equalsIgnoreCase(rm.getRole().getName()));

        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can invite users");
        }

        String targetEmail = email.trim().toLowerCase(Locale.ROOT);

        // Check if user already exists and belongs to organization
        Optional<AppUser> targetUser = appUserRepository.findByEmailIgnoreCase(targetEmail);
        if (targetUser.isPresent()) {
            Optional<UserOrganizationMapping> existingMapping = userOrganizationMappingRepository
                    .findByUser_IdAndOrganization_Id(targetUser.get().getId(), org.getId());
            if (existingMapping.isPresent() && "ACTIVE".equals(existingMapping.get().getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this organization");
            }
        }

        // Deactivate old pending invites for this email/org
        organizationInviteRepository.findByOrganization_IdAndEmailIgnoreCaseAndStatus(org.getId(), targetEmail, "INVITED")
                .ifPresent(invite -> {
                    invite.setStatus("EXPIRED");
                    invite.setUpdatedAt(LocalDateTime.now());
                    organizationInviteRepository.save(invite);
                });

        OrganizationInvite invite = new OrganizationInvite();
        invite.setOrganization(org);
        invite.setEmail(targetEmail);
        invite.setInvitedBy(inviter);
        invite.setStatus("INVITED");
        invite.setCreatedAt(LocalDateTime.now());
        invite.setUpdatedAt(LocalDateTime.now());

        return organizationInviteRepository.save(invite);
    }

    @Transactional
    public void acceptInvite(UUID inviteId, String userEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized"));

        OrganizationInvite invite = organizationInviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));

        if (!invite.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation was sent to another email address");
        }

        if (!"INVITED".equals(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is not active");
        }

        invite.setStatus("ACCEPTED");
        invite.setUpdatedAt(LocalDateTime.now());
        organizationInviteRepository.save(invite);

        // Create or update mapping
        UserOrganizationMapping mapping = userOrganizationMappingRepository
                .findByUser_IdAndOrganization_Id(user.getId(), invite.getOrganization().getId())
                .orElseGet(() -> {
                    UserOrganizationMapping m = new UserOrganizationMapping();
                    m.setUser(user);
                    m.setOrganization(invite.getOrganization());
                    return m;
                });

        mapping.setStatus("ACTIVE");
        mapping.setAssignedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());
        userOrganizationMappingRepository.save(mapping);

        assignRoleToUserInOrg(user, invite.getOrganization(), "DEV");
    }

    @Transactional
    public OrganizationJoinRequest requestToJoin(UUID orgId, String userEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized"));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        if ("PERSONAL".equalsIgnoreCase(org.getOrganizationType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personal workspaces are invite-only");
        }

        // Check if user is already a member
        Optional<UserOrganizationMapping> existingMapping = userOrganizationMappingRepository
                .findByUser_IdAndOrganization_Id(user.getId(), org.getId());
        if (existingMapping.isPresent() && "ACTIVE".equals(existingMapping.get().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already a member of this organization");
        }

        // Deactivate old pending requests
        organizationJoinRequestRepository.findByOrganization_IdAndUser_IdAndStatus(org.getId(), user.getId(), "PENDING")
                .ifPresent(req -> {
                    req.setStatus("REJECTED");
                    req.setUpdatedAt(LocalDateTime.now());
                    organizationJoinRequestRepository.save(req);
                });

        OrganizationJoinRequest req = new OrganizationJoinRequest();
        req.setOrganization(org);
        req.setUser(user);
        req.setStatus("PENDING");
        req.setCreatedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());

        return organizationJoinRequestRepository.save(req);
    }

    @Transactional
    public void approveJoinRequest(UUID requestId, String approverEmail) {
        AppUser approver = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(approverEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Approver not authorized"));

        OrganizationJoinRequest req = organizationJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found"));

        if (!"PENDING".equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join request is already processed");
        }

        // Verify approver has ADMIN role in request's organization
        boolean isAdmin = userRoleMappingRepository.findByUser_IdAndOrganization_Id(approver.getId(), req.getOrganization().getId()).stream()
                .anyMatch(rm -> "ADMIN".equalsIgnoreCase(rm.getRole().getName()));

        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can approve join requests");
        }

        req.setStatus("APPROVED");
        req.setUpdatedAt(LocalDateTime.now());
        organizationJoinRequestRepository.save(req);

        // Add user to organization
        UserOrganizationMapping mapping = userOrganizationMappingRepository
                .findByUser_IdAndOrganization_Id(req.getUser().getId(), req.getOrganization().getId())
                .orElseGet(() -> {
                    UserOrganizationMapping m = new UserOrganizationMapping();
                    m.setUser(req.getUser());
                    m.setOrganization(req.getOrganization());
                    return m;
                });

        mapping.setStatus("ACTIVE");
        mapping.setAssignedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());
        userOrganizationMappingRepository.save(mapping);

        assignRoleToUserInOrg(req.getUser(), req.getOrganization(), "DEV");
    }

    @Transactional
    public void rejectJoinRequest(UUID requestId, String approverEmail) {
        AppUser approver = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(approverEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Approver not authorized"));

        OrganizationJoinRequest req = organizationJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found"));

        if (!"PENDING".equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join request is already processed");
        }

        boolean isAdmin = userRoleMappingRepository.findByUser_IdAndOrganization_Id(approver.getId(), req.getOrganization().getId()).stream()
                .anyMatch(rm -> "ADMIN".equalsIgnoreCase(rm.getRole().getName()));

        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can reject join requests");
        }

        req.setStatus("REJECTED");
        req.setUpdatedAt(LocalDateTime.now());
        organizationJoinRequestRepository.save(req);
    }

    @Transactional
    public void removeMember(UUID orgId, String userId, String actorEmail) {
        AppUser actor = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(actorEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Actor not authorized"));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        boolean isAdmin = userRoleMappingRepository.findByUser_IdAndOrganization_Id(actor.getId(), org.getId()).stream()
                .anyMatch(rm -> "ADMIN".equalsIgnoreCase(rm.getRole().getName()));

        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can remove members");
        }

        UserOrganizationMapping mapping = userOrganizationMappingRepository
                .findByUser_IdAndOrganization_IdAndStatus(userId, org.getId(), "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not an active member of this organization"));

        // Check if removing self and is the last admin
        if (userId.equals(actor.getId())) {
            List<UserRoleMapping> adminMappings = userRoleMappingRepository.findAll().stream()
                    .filter(rm -> rm.getOrganization().getId().equals(org.getId()) && "ADMIN".equalsIgnoreCase(rm.getRole().getName()))
                    .toList();
            if (adminMappings.size() <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove the last administrator from the organization");
            }
        }

        mapping.setStatus("REMOVED");
        mapping.setUpdatedAt(LocalDateTime.now());
        userOrganizationMappingRepository.save(mapping);

        // Delete user's role mappings in this organization
        List<UserRoleMapping> userRoles = userRoleMappingRepository.findByUser_IdAndOrganization_Id(userId, org.getId());
        userRoleMappingRepository.deleteAll(userRoles);
    }

    @Transactional
    public void changeMemberRole(UUID orgId, String userId, String targetRoleName, String actorEmail) {
        AppUser actor = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(actorEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Actor not authorized"));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        boolean isAdmin = userRoleMappingRepository.findByUser_IdAndOrganization_Id(actor.getId(), org.getId()).stream()
                .anyMatch(rm -> "ADMIN".equalsIgnoreCase(rm.getRole().getName()));

        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can change member roles");
        }

        AppUser targetUser = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member user not found"));

        UserOrganizationMapping mapping = userOrganizationMappingRepository
                .findByUser_IdAndOrganization_IdAndStatus(userId, org.getId(), "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not an active member of this organization"));

        AppRole role = appRoleRepository.findByNameIgnoreCase(targetRoleName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + targetRoleName));

        // Delete existing role mapping in this organization
        List<UserRoleMapping> userRoles = userRoleMappingRepository.findByUser_IdAndOrganization_Id(userId, org.getId());
        userRoleMappingRepository.deleteAll(userRoles);

        UserRoleMapping newMapping = new UserRoleMapping();
        newMapping.setUser(targetUser);
        newMapping.setOrganization(org);
        newMapping.setRole(role);
        newMapping.setAssignedAt(LocalDateTime.now());
        newMapping.setUpdatedAt(LocalDateTime.now());
        userRoleMappingRepository.save(newMapping);
    }

    @Transactional
    public String createApiKey(UUID orgId, String name, String userEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized"));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        // Check active membership
        userOrganizationMappingRepository.findByUser_IdAndOrganization_IdAndStatus(user.getId(), org.getId(), "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not an active member of this organization"));

        String rawKey = "lk_" + UUID.randomUUID().toString().replace("-", "");
        String hash = hashKey(rawKey);

        OrganizationApiKey apiKey = new OrganizationApiKey();
        apiKey.setOrganization(org);
        apiKey.setName(name == null || name.isBlank() ? "LynkLog SDK Key" : name.trim());
        apiKey.setApiKeyHash(hash);
        apiKey.setCreatedBy(user);
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setActive(true);

        organizationApiKeyRepository.save(apiKey);

        return rawKey;
    }

    @Transactional(readOnly = true)
    public List<OrganizationApiKey> listApiKeys(UUID orgId, String userEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized"));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        userOrganizationMappingRepository.findByUser_IdAndOrganization_IdAndStatus(user.getId(), org.getId(), "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not an active member of this organization"));

        return organizationApiKeyRepository.findByOrganization_Id(org.getId());
    }

    private void assignRoleToUserInOrg(AppUser user, Organization org, String roleName) {
        AppRole role = appRoleRepository.findByNameIgnoreCase(roleName)
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName(roleName.toUpperCase(Locale.ROOT));
                    r.setDescription("Provisioned " + roleName + " role");
                    r.setCreatedAt(LocalDateTime.now());
                    return appRoleRepository.save(r);
                });

        if (!userRoleMappingRepository.existsByUser_IdAndOrganization_IdAndRole_Id(user.getId(), org.getId(), role.getId())) {
            UserRoleMapping mapping = new UserRoleMapping();
            mapping.setUser(user);
            mapping.setOrganization(org);
            mapping.setRole(role);
            mapping.setAssignedAt(LocalDateTime.now());
            mapping.setUpdatedAt(LocalDateTime.now());
            userRoleMappingRepository.save(mapping);
        }
    }

    private String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
