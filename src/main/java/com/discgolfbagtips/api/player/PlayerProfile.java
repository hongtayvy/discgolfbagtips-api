package com.discgolfbagtips.api.player;

import jakarta.validation.constraints.NotNull;

/** The lightweight profile the front end collects: three questions, no account. */
public record PlayerProfile(
        @NotNull SkillLevel skillLevel,
        @NotNull ThrowingStyle throwingStyle,
        @NotNull CourseType courseType) {

    public String describe() {
        return "The player is %s who %s and mostly plays %s."
                .formatted(skillLevel.description(), throwingStyle.description(), courseType.description());
    }
}
