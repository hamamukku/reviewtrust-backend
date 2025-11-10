/*
 * Deprecated security configuration.
 *
 * This class remains solely for historical reference to the MVP era of the application,
 * which used a fixed Basic-authenticated admin user and CORS separation. With the
 * introduction of JWT-based authentication and more flexible user management,
 * this configuration is no longer active. It deliberately omits any Spring
 * annotations (@Configuration, @EnableWebSecurity) and bean definitions so that
 * it does not get picked up during component scanning.
 */
package com.hamas.reviewtrust.config;

@Deprecated
public class WebSecurityConfig {
    // intentionally empty
}

