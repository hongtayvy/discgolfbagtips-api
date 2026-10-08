package com.discgolfbagtips.api.profile;

/**
 * What happened to an anonymous session's saved bags when a user signed in.
 *
 * @param moved      bags now owned by the account
 * @param renamed    of those, how many took a " (2)"-style suffix because the account already held
 *                   a bag of that name
 * @param leftBehind bags that stayed with the session because the account was at its limit
 */
public record ClaimResult(int moved, int renamed, int leftBehind) {

    static final ClaimResult NOTHING = new ClaimResult(0, 0, 0);
}
