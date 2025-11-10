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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link RrdUtil#ensureValidRrdDataSourceName(String)}.
 *
 * These tests validate that the method correctly returns or hashes input names
 * according to the 20-character limit defined for RRD Data Source names.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RrdUtilTest
{
    /**
     * Verifies that a short name (less than 20 characters) is returned unchanged.
     */
    @Test
    public void shortNameReturnsUnchanged()
    {
        // Setup test fixture.
        String input = "ShortName123";

        // Execute System under Test.
        String result = RrdUtil.ensureValidRrdDataSourceName(input);

        // Verify Result.
        assertEquals("Names ≤ 20 characters should be returned unchanged", input, result);
    }

    /**
     * Verifies that a name exactly 20 characters long is returned unchanged.
     */
    @Test
    public void exactLengthNameReturnsUnchanged()
    {
        // Setup test fixture.
        String input = "ABCDEFGHIJKLMNOPQRST"; // exactly 20 chars

        // Execute System under Test.
        String result = RrdUtil.ensureValidRrdDataSourceName(input);

        // Verify Result.
        assertEquals("Names with exactly 20 characters should be returned unchanged", input, result);
    }

    /**
     * Verifies that a name longer than 20 characters is hashed into a 20-character result.
     */
    @Test
    public void longNameGetsHashed()
    {
        // Setup test fixture.
        String input = "ThisIsALongDataSourceNameExceeding20Chars";

        // Execute System under Test.
        String result = RrdUtil.ensureValidRrdDataSourceName(input);

        // Verify Result.
        assertNotNull("Result should not be null", result);
        assertEquals("Hashed names must be exactly 20 characters long", 20, result.length());
    }

    /**
     * Verifies that an empty string input is returned unchanged.
     */
    @Test
    public void emptyNameReturnsUnchanged()
    {
        // Setup test fixture.
        String input = "";

        // Execute System under Test.
        String result = RrdUtil.ensureValidRrdDataSourceName(input);

        // Verify Result.
        assertEquals("Empty names should be returned unchanged", input, result);
    }

    /**
     * Verifies that passing null throws a NullPointerException due to @Nonnull annotation.
     */
    @Test(expected = NullPointerException.class)
    public void nullInputThrowsException() {
        // Setup test fixture.
        String input = null;

        // Execute System under Test.
        RrdUtil.ensureValidRrdDataSourceName(input);

        // Verify Result. (Handled by expected exception)
    }

    /**
     * Verifies that the hashing is deterministic - same input yields same hash.
     */
    @Test
    public void hashIsDeterministic()
    {
        // Setup test fixture.
        String input = "SomeVeryLongDataSourceNameThatNeedsHashing";

        // Execute System under Test.
        String result1 = RrdUtil.ensureValidRrdDataSourceName(input);
        String result2 = RrdUtil.ensureValidRrdDataSourceName(input);

        // Verify Result.
        assertEquals("Hash output should be consistent for the same input", result1, result2);
    }
}
