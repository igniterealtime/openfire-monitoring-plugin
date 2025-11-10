/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.reporting.stats;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility methods for working with Round Robin Database instances (and/or Java's implementation thereof).
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RrdUtil
{
    // 36^20 as a BigInteger for modulo operation
    private static final BigInteger MOD_36_20 = BigInteger.valueOf(36).pow(20);

    /**
     * A RRD Data Source name cannot be longer than 20 characters. This method returns the provided value if it's not
     * longer than that, or a 20-character hash of the provided value when it is.
     *
     * @param rrdDataSourceName The name for which to return a valid variant
     * @return A value not longer than 20 characters.
     */
    public static String ensureValidRrdDataSourceName(@Nonnull final String rrdDataSourceName)
    {
        if (rrdDataSourceName.length() > 20) {
            return generateHash(rrdDataSourceName);
        } else {
            return rrdDataSourceName;
        }
    }

    /**
     * Generates a 20-character base-36 hash of the given input string using SHA-256.
     *
     * This method hashes the input string with SHA-256, then compresses the full 256-bit hash into exactly 20 base-36
     * characters by taking the modulo 36^20. Leading zeros are added if necessary to ensure the length is exactly 20.
     *
     * Characteristics of the output:
     * <ul>
     * <li>Exactly 20 characters long.</li>
     * <li>Uses the full entropy of SHA-256.</li>
     * <li>Deterministic: same input always produces the same hash.</li>
     * <li>Extremely low probability of collisions due to SHA-256 uniformity.</li>
     * </ul>
     * </p>
     *
     * @param input the input string to hash; must not be null
     * @return a 20-character base-36 encoded string representing the hash of the input
     * @throws RuntimeException if the SHA-256 algorithm is not available
     */
    public static String generateHash(@Nonnull final String input)
    {
        try {
            // Hash input with SHA-256
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert full hash to positive BigInteger
            final BigInteger hashInt = new BigInteger(1, hashBytes);

            // Compress to fit 20 base-36 chars
            final BigInteger compressed = hashInt.mod(MOD_36_20);

            // Convert to base-36 string
            final String base36 = compressed.toString(36);

            // Pad with leading zeros to ensure exactly 20 characters
            return String.format("%20s", base36).replace(' ', '0');
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
