/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.opensearch.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.common.utils.IdUtils;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;

public class ParquetEmbeddingsParserWithBlobrefTest {

    private static final ClassLoader classLoader = ParquetEmbeddingsParserWithBlobrefTest.class.getClassLoader();

    public static final String TEST_EMBEDDINGS_PARQUET_FILENAME = "The Jungle Book.parquet";

    @Test
    void parseShouldReturnExpectedEmbeddings() {
        File parquetFile = fileOf(TEST_EMBEDDINGS_PARQUET_FILENAME);
        assertTrue(parquetFile.exists(), "The test parquet file must exist");

        Blob blob = Blobs.createBlob(parquetFile);
        assertNotNull(blob, "The blob must not be null");

        ParquetEmbeddingsParser parser = new ParquetEmbeddingsParser();
        String parentId = UUID.randomUUID().toString();
        String embeddingsType = "amazon-titan-text-embeddings-v2";
        List<EmbeddingModel> embeddings = parser.parse(blob, "repository", parentId,
                embeddingsType).stream().toList();

        assertEquals(3, embeddings.size(), "Unexpected embedding count");

        List<ExpectedEmbedding> expected = List.of(
            new ExpectedEmbedding("id-0", embeddingsType, " The Project Gutenberg eBook of The Jungle Book    This ebook is for the use of anyone anywhere in the United States and   most other parts of the world at no cost and with almost no restrictions   whatsoever. You may copy it, give it away or re-use it under the terms   of the Project Gutenberg License included with this ebook or online   at www.gutenberg.org. If you are not located in the United States,   you will have to check the laws of the country where you are located   before using this eBook.      Title: The Jungle Book    Author: Rudyard Kipling    Release date: January 16, 2006 \\[eBook \\#236\\]                   Most recently updated: May 1, 2023    Language: English    \\*\\*\\* START OF THE PROJECT GUTENBERG EBOOK THE JUNGLE BOOK \\*\\*\\*    THE JUNGLE BOOK    By Rudyard Kipling    Contents         Mowgli's Brothers        Hunting-Song of the Seeonee Pack        Kaa's Hunting        Road-Song of the Bandar-Log        \"Tiger\\! Tiger\\!\"         Mowgli's Song        The White Seal        Lukannon        \"Rikki-Tikki-Tavi\"         Darzee's Chant        Toomai of the Elephants        Shiv and the Grasshopper        Her Majesty's Servants        Parade Song of the Camp Animals    Mowgli's Brothers         Now Rann the Kite brings home the night           That Mang the Bat sets free--        The herds are shut in byre and hut           For loosed till dawn are we.        This is the hour of pride and power,           Talon and tush and claw.        Oh, hear the call\\!--Good hunting all            That keep the Jungle Law\\!        \\_Night-Song in the Jungle\\_    It was seven o'clock of a very warm evening in the Seeonee hills when   Father Wolf woke up from his day's rest, scratched himself, yawned, and   spread out his paws one after the other to get rid of the sleepy feeling   in their tips. Mother Wolf lay with her big gray nose dropped across her   four tumbling, squealing cubs, and the moon shone into the mouth of the   cave where they all lived. \"Augrh\\!\" said Father Wolf. \"It is time to   hunt again.\" He was going to spring down hill when a little shadow with   a bushy tail crossed the threshold and whined: \"Good luck go with you, O   Chief of the Wolves. And good luck and strong white teeth go with noble   children that they may never forget the hungry in this world.\"    It was the jackal--Tabaqui, the Dish-licker--and the wolves of India   despise Tabaqui because he runs about making mischief, and telling   tales, and eating rags and pieces of leather from the village   rubbish-heaps. But they are afraid of him too, because Tabaqui, more   than anyone else in the jungle, is apt to go mad, and then he forgets   that he was ever afraid of anyone, and runs through the forest biting   everything in his way. Even the tiger runs and hides when little Tabaqui   goes mad, for madness is the most disgraceful thing that can overtake   a wild creature. We call it hydrophobia, but they call it dewanee--the   madness--and run.",
                List.of(0.9276160731432078, 0.3888523872999916, 0.7236868711252606, 0.7361367955012308, 0.7361367955012308), LocationExpectation.standard(),"blob-f7bdc5f9-9239-43d2-ac94-63f7de5e089d"),
            new ExpectedEmbedding("id-1", embeddingsType, " \"Enter, then, and look,\" said Father Wolf stiffly, \"but there is no food   here.\"    \"For a wolf, no,\" said Tabaqui, \"but for so mean a person as myself a   dry bone is a good feast. Who are we, the Gidur-log \\[the jackal people\\],   to pick and choose?\" He scuttled to the back of the cave, where he   found the bone of a buck with some meat on it, and sat cracking the end   merrily.      \"All thanks for this good meal,\" he said, licking his lips. \"How   beautiful are the noble children\\! How large are their eyes\\! And so young   too\\! Indeed, indeed, I might have remembered that the children of kings   are men from the beginning.\"    Now, Tabaqui knew as well as anyone else that there is nothing so   unlucky as to compliment children to their faces. It pleased him to see   Mother and Father Wolf look uncomfortable.      Tabaqui sat still, rejoicing in the mischief that he had made, and then   he said spitefully:      \"Shere Khan, the Big One, has shifted his hunting grounds. He will hunt   among these hills for the next moon, so he has told me.\"    Shere Khan was the tiger who lived near the Waingunga River, twenty   miles away.      \"He has no right\\!\" Father Wolf began angrily--\"By the Law of the Jungle   he has no right to change his quarters without due warning. He will   frighten every head of game within ten miles, and I--I have to kill for   two, these days.\"    \"His mother did not call him Lungri \\[the Lame One\\] for nothing,\" said   Mother Wolf quietly. \"He has been lame in one foot from his birth. That   is why he has only killed cattle. Now the villagers of the Waingunga are   angry with him, and he has come here to make our villagers angry.",
                List.of(0.6467109419808799, 0.1768389911926278, 0.4593046569524648, 0.1643697098016449, 0.8834441956452832), LocationExpectation.standard(),"blob-493aa93b-9ea5-4c77-a5c1-5bd2a3ad9eb0"),
            new ExpectedEmbedding("id-2", embeddingsType, " They will scour the jungle for him when he is far away, and we and our   children must run when the grass is set alight. Indeed, we are very   grateful to Shere Khan\\!\"    \"Shall I tell him of your gratitude?\" said Tabaqui.      \"Out\\!\" snapped Father Wolf. \"Out and hunt with thy master. Thou hast   done harm enough for one night.\"    \"I go,\" said Tabaqui quietly. \"Ye can hear Shere Khan below in the   thickets. I might have saved myself the message.\"    Father Wolf listened, and below in the valley that ran down to a little   river he heard the dry, angry, snarly, singsong whine of a tiger who has   caught nothing and does not care if all the jungle knows it.      \"The fool\\!\" said Father Wolf. \"To begin a night's work with that noise\\!   Does he think that our buck are like his fat Waingunga bullocks?\"    \"H'sh. It is neither bullock nor buck he hunts to-night,\" said Mother   Wolf. \"It is Man.\"    The whine had changed to a sort of humming purr that seemed to come   from every quarter of the compass. It was the noise that bewilders   woodcutters and gypsies sleeping in the open, and makes them run   sometimes into the very mouth of the tiger.      \"Man\\!\" said Father Wolf, showing all his white teeth. \"Faugh\\! Are there   not enough beetles and frogs in the tanks that he must eat Man, and on   our ground too\\!\"    The Law of the Jungle, which never orders anything without a reason,   forbids every beast to eat Man except when he is killing to show his   children how to kill, and then he must hunt outside the hunting grounds   of his pack or tribe. The real reason for this is that man-killing   means, sooner or later, the arrival of white men on elephants, with   guns, and hundreds of brown men with gongs and rockets and torches.   Then everybody in the jungle suffers. The reason the beasts give among   themselves is that Man is the weakest and most defenseless of all living   things, and it is unsportsmanlike to touch him. They say too--and it is   true--that man-eaters become mangy, and lose their teeth.",
                List.of(0.7384569961881483, 0.552512268185088, 0.03297788475095964,0.9608535027581576,0.44838848320995595), LocationExpectation.standard(),"blob-2ad7dd4a-33bb-441e-97b8-7a4d7423e7d1"));

        for (int i = 0; i < embeddings.size(); i++) {
            EmbeddingModel model = embeddings.get(i);
            ExpectedEmbedding expectedEntry = expected.get(i);

            assertEquals(parentId, model.docId());
            assertEquals(expectedEntry.type(), model.type());
            assertEquals(expectedEntry.blobref(), model.blobRef());
            assertEquals(
                    normalize(expectedEntry.text()),
                    normalize(model.text())
            );
            assertEquals(
                    normalize(expectedEntry.text()),
                    normalize(model.text())
            );
        }
    }

    @Test
    void parseShouldAssignDeterministicUuidWhenMissing() {
        Blob blob = blobOf(TEST_EMBEDDINGS_PARQUET_FILENAME);

        ParquetEmbeddingsParser parser = new ParquetEmbeddingsParser();
        List<EmbeddingModel> embeddings = parser.parse(blob, "repository", UUID.randomUUID().toString(), "type")
                .stream()
                .toList();

        assertEquals(3, embeddings.size());
        embeddings.forEach(embedding -> assertTrue(IdUtils.isValidUUID(embedding.id())));
    }

    // Helper methods

    /**
     * Returns a test {@link Blob} created from the test embeddings Parquet file.
     *
     * @return a {@link Blob} containing the test embeddings data
     */
    public static Blob testBlob() {
        return blobOf(TEST_EMBEDDINGS_PARQUET_FILENAME);
    }

    static File fileOf(String name) {
        try {
            return new File(classLoader.getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot resolve " + name + " from the test classpath", e);
        }
    }

     static Blob blobOf(String name) {
        return Blobs.createBlob(fileOf(name));
    }

    record ExpectedEmbedding(String sourceId, String type, String text, List<Double> vector,
            LocationExpectation location, String blobref) {
    }

    record LocationExpectation(int page, int paragraph, int left, int top, int right, int bottom, double start,
            double end, String sheet, int row, int column) {

        static LocationExpectation standard() {
            return new LocationExpectation(0, 2, 0, 0, 0, 0, 0.0, 0.0, "", 0, 0);
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;

        return s
                .replaceAll("\\s+", " ")   // collapse all whitespace
                .trim();
    }
}
