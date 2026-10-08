package com.smartfix.community.domain;

/**
 * The topic a community question belongs to.
 *
 * <p>A closed list rather than a free-text field or a lookup table. Plan section 9.5
 * fixes these five values, so a question can be filed and filtered without an
 * administrator curating a taxonomy first; adding a value later is a code change
 * plus a migration, which is the cost of keeping the filter keys stable and
 * translatable.</p>
 *
 * <p>Stored as its name ({@code @Enumerated(EnumType.STRING)}), so reordering the
 * constants never rewrites existing rows.</p>
 */
public enum CommunityCategory {

    HARDWARE,
    SOFTWARE,
    NETWORK,
    PERIPHERAL,
    OTHER
}
