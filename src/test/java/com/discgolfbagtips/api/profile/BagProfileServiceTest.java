package com.discgolfbagtips.api.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.player.CourseConditions;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.api.recommendation.dto.BagDiscRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BagProfileServiceTest {

    private final BagProfileRepository repository = mock(BagProfileRepository.class);
    private final BagProfileService service =
            new BagProfileService(repository, JsonMapper.builder().build());

    private final BagAnalysisRequest request = new BagAnalysisRequest(
            List.of(new BagDiscRequest(null, "Buzzz", "Discraft", "ESP", 177, null)),
            new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.FOREHAND, CourseType.WOODED),
            new CourseConditions(WeatherCondition.WINDY), null, null);

    @Test
    void ownerKeysNameTheirSource() {
        assertThat(BagProfileService.sessionOwnerKey("abc")).isEqualTo("session:abc");
        assertThat(BagProfileService.userOwnerKey("uuid-1")).isEqualTo("user:uuid-1");
    }

    @Test
    void savingStoresTheWholeRequestSoItCanBeReplayed() {
        when(repository.findByOwnerKeyAndNameIgnoreCase(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.countByOwnerKey(anyString())).thenReturn(0L);
        when(repository.save(any(BagProfile.class))).thenAnswer(i -> i.getArgument(0));

        BagProfileDetail saved = service.save("session:a", "Wooded", "east coast", request);

        assertThat(saved.profile().name()).isEqualTo("Wooded");
        assertThat(saved.profile().discCount()).isEqualTo(1);
        assertThat(saved.request().bagOrEmpty()).hasSize(1);
    }

    /** Typing the same name again means "replace what I saved", not "create a duplicate". */
    @Test
    void savingUnderAnExistingNameReplacesIt() {
        BagProfile existing = new BagProfile("session:a", "Wooded", null, "{}", 0, null);
        when(repository.findByOwnerKeyAndNameIgnoreCase("session:a", "Wooded"))
                .thenReturn(Optional.of(existing));

        BagProfileDetail saved = service.save("session:a", "Wooded", "updated", request);

        assertThat(saved.profile().discCount()).isEqualTo(1);
        assertThat(saved.profile().description()).isEqualTo("updated");
        verify(repository, never()).save(any(BagProfile.class));
    }

    @Test
    void trimsTheNameAndRejectsABlankOne() {
        when(repository.findByOwnerKeyAndNameIgnoreCase(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.countByOwnerKey(anyString())).thenReturn(0L);
        when(repository.save(any(BagProfile.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.save("session:a", "  Wooded  ", null, request).profile().name())
                .isEqualTo("Wooded");

        assertThatThrownBy(() -> service.save("session:a", "   ", null, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("needs a name");
    }

    @Test
    void refusesToLetOneSessionFillTheTable() {
        when(repository.findByOwnerKeyAndNameIgnoreCase(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.countByOwnerKey("session:a")).thenReturn(20L);

        assertThatThrownBy(() -> service.save("session:a", "One more", null, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already have 20 saved bags");
        verify(repository, never()).save(any(BagProfile.class));
    }

    @Test
    void oneOwnerCannotLoadAnothersProfile() {
        when(repository.findByIdAndOwnerKey(any(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.load("session:b", java.util.UUID.randomUUID()))
                .isInstanceOf(com.discgolfbagtips.api.common.NotFoundException.class);
    }

    @Test
    void anUnreadablePayloadIsReportedRatherThanCrashing() {
        BagProfile corrupt = new BagProfile("session:a", "Old", null, "not json at all", 3, null);
        when(repository.findByIdAndOwnerKey(any(), anyString())).thenReturn(Optional.of(corrupt));

        assertThatThrownBy(() -> service.load("session:a", java.util.UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("older format");
    }

    /** The reason ownerKey is a string: accounts become an update, not a migration. */
    @Test
    void sessionProfilesCanBeClaimedByAUser() {
        BagProfile wooded = new BagProfile("session:a", "Wooded", null, "{}", 1, null);
        BagProfile open = new BagProfile("session:a", "Open", null, "{}", 1, null);
        when(repository.findAllByOwnerKeyOrderByUpdatedAtDesc("session:a")).thenReturn(List.of(wooded, open));
        when(repository.findAllByOwnerKeyOrderByUpdatedAtDesc("user:u1")).thenReturn(List.of());

        assertThat(service.claimSessionProfiles("a", "u1")).isEqualTo(new ClaimResult(2, 0, 0));
        assertThat(wooded.ownerKey()).isEqualTo("user:u1");
        assertThat(open.ownerKey()).isEqualTo("user:u1");
        assertThat(wooded.name()).isEqualTo("Wooded");
    }

    /**
     * Signing in on a second device: the account already has a "Wooded". A bulk UPDATE would violate
     * the unique name constraint; overwriting would lose a bag. Renaming keeps both.
     */
    @Test
    void aClashingNameIsRenamedRatherThanLost() {
        BagProfile incoming = new BagProfile("session:a", "wooded", null, "{}", 1, null);
        when(repository.findAllByOwnerKeyOrderByUpdatedAtDesc("session:a")).thenReturn(List.of(incoming));
        when(repository.findAllByOwnerKeyOrderByUpdatedAtDesc("user:u1")).thenReturn(List.of(
                new BagProfile("user:u1", "Wooded", null, "{}", 1, null),
                new BagProfile("user:u1", "wooded (2)", null, "{}", 1, null)));

        assertThat(service.claimSessionProfiles("a", "u1")).isEqualTo(new ClaimResult(1, 1, 0));
        assertThat(incoming.name()).isEqualTo("wooded (3)");
        assertThat(incoming.ownerKey()).isEqualTo("user:u1");
    }

    @Test
    void whatDoesNotFitUnderTheLimitStaysWithTheSession() {
        BagProfile newest = new BagProfile("session:a", "Newest", null, "{}", 1, null);
        BagProfile older = new BagProfile("session:a", "Older", null, "{}", 1, null);
        when(repository.findAllByOwnerKeyOrderByUpdatedAtDesc("session:a")).thenReturn(List.of(newest, older));
        List<BagProfile> full = java.util.stream.IntStream.range(0, 19)
                .mapToObj(i -> new BagProfile("user:u1", "Bag " + i, null, "{}", 1, null)).toList();
        when(repository.findAllByOwnerKeyOrderByUpdatedAtDesc("user:u1")).thenReturn(full);

        assertThat(service.claimSessionProfiles("a", "u1")).isEqualTo(new ClaimResult(1, 0, 1));
        assertThat(newest.ownerKey()).isEqualTo("user:u1");
        assertThat(older.ownerKey()).isEqualTo("session:a");
    }

    @Test
    void claimingAnEmptySessionIsANoOp() {
        when(repository.findAllByOwnerKeyOrderByUpdatedAtDesc("session:a")).thenReturn(List.of());

        assertThat(service.claimSessionProfiles("a", "u1")).isEqualTo(new ClaimResult(0, 0, 0));
    }

    @Test
    void aRenamedMaximumLengthNameStillFitsTheColumn() {
        String longName = "x".repeat(120);

        String renamed = BagProfileService.firstFreeName(longName, java.util.Set.of(longName));

        assertThat(renamed).hasSize(120).endsWith(" (2)");
    }
}
