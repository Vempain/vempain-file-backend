package fi.poltsi.vempain.file.tools;

import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Extended unit tests (UTC) for {@link MetadataTool} JSON-based extraction methods.
 */
class MetadataToolExtendedUTC {

    // ------------------------------------------------------------------
    // extractDescription
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractDescription")
    class ExtractDescription {

        @Test
        void returnsXmpDescription() {
            var json = new JSONObject();
            var xmp = new JSONObject();
            xmp.put("Description", "A nice photo");
            json.put("XMP", xmp);
            assertThat(MetadataTool.extractDescription(json)).isEqualTo("A nice photo");
        }

        @Test
        void fallsBackToIptcCaption() {
            var json = new JSONObject();
            var iptc = new JSONObject();
            iptc.put("Caption-Abstract", "IPTC caption");
            json.put("IPTC", iptc);
            assertThat(MetadataTool.extractDescription(json)).isEqualTo("IPTC caption");
        }

        @Test
        void returnsNull_whenNoDescription() {
            var json = new JSONObject();
            assertNull(MetadataTool.extractDescription(json));
        }
    }

    // ------------------------------------------------------------------
    // extractMimetype
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractMimetype")
    class ExtractMimetype {

        @Test
        void returnsFileMimetype() {
            var json = new JSONObject();
            var file = new JSONObject();
            file.put("MIMEType", "image/jpeg");
            json.put("File", file);
            assertThat(MetadataTool.extractMimetype(json)).isEqualTo("image/jpeg");
        }

        @Test
        void returnsXmpMimetype() {
            var json = new JSONObject();
            var xmp = new JSONObject();
            xmp.put("MIMEType", "application/pdf");
            json.put("XMP", xmp);
            assertThat(MetadataTool.extractMimetype(json)).isEqualTo("application/pdf");
        }

        @Test
        void returnsNull_whenNoMimetype() {
            assertNull(MetadataTool.extractMimetype(new JSONObject()));
        }
    }

    // ------------------------------------------------------------------
    // extractOriginalDateTime
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractOriginalDateTime")
    class ExtractOriginalDateTime {

        @Test
        void returnsExifIFDDateTime() {
            var json = new JSONObject();
            var exifIFD = new JSONObject();
            exifIFD.put("DateTimeOriginal", "2023:06:15 12:34:56");
            json.put("ExifIFD", exifIFD);
            assertThat(MetadataTool.extractOriginalDateTime(json)).isEqualTo("2023:06:15 12:34:56");
        }

        @Test
        void returnsCompositeDateTime() {
            var json = new JSONObject();
            var composite = new JSONObject();
            composite.put("DateTimeOriginal", "2022:01:01 00:00:00");
            json.put("Composite", composite);
            assertThat(MetadataTool.extractOriginalDateTime(json)).isEqualTo("2022:01:01 00:00:00");
        }

        @Test
        void returnsNull_whenNoDateTime() {
            assertNull(MetadataTool.extractOriginalDateTime(new JSONObject()));
        }
    }

    // ------------------------------------------------------------------
    // extractOriginalSecondFraction
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractOriginalSecondFraction")
    class ExtractOriginalSecondFraction {

        @Test
        void returnsSubSecValue() {
            var json = new JSONObject();
            var exifIFD = new JSONObject();
            exifIFD.put("SubSecTimeOriginal", 76);
            json.put("ExifIFD", exifIFD);
            assertThat(MetadataTool.extractOriginalSecondFraction(json)).isEqualTo(76);
        }

        @Test
        void returnsZero_whenNotPresent() {
            assertThat(MetadataTool.extractOriginalSecondFraction(new JSONObject())).isEqualTo(0);
        }
    }

    // ------------------------------------------------------------------
    // extractOriginalDocumentId
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractOriginalDocumentId")
    class ExtractOriginalDocumentId {

        @Test
        void returnsDocumentId() {
            var json = new JSONObject();
            var xmpMM = new JSONObject();
            xmpMM.put("OriginalDocumentID", "xmp.did:ABC123");
            json.put("XMP-xmpMM", xmpMM);
            assertThat(MetadataTool.extractOriginalDocumentId(json)).isEqualTo("xmp.did:ABC123");
        }

        @Test
        void fallsBackToDocumentID() {
            var json = new JSONObject();
            var xmpMM = new JSONObject();
            xmpMM.put("DocumentID", "xmp.did:DEF456");
            json.put("XMP-xmpMM", xmpMM);
            assertThat(MetadataTool.extractOriginalDocumentId(json)).isEqualTo("xmp.did:DEF456");
        }

        @Test
        void returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractOriginalDocumentId(new JSONObject()));
        }
    }

    // ------------------------------------------------------------------
    // extractSubjects
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractSubjects")
    class ExtractSubjects {

        @Test
        void returnsSubjectArray() {
            var json = new JSONObject();
            var xmp = new JSONObject();
            xmp.put("Subject", new org.json.JSONArray(List.of("nature", "landscape")));
            json.put("XMP", xmp);
            var subjects = MetadataTool.extractSubjects(json);
            assertThat(subjects).containsExactlyInAnyOrder("nature", "landscape");
        }

        @Test
        void returnsSingleSubjectAsString() {
            var json = new JSONObject();
            var xmp = new JSONObject();
            xmp.put("Subject", "singleKeyword");
            json.put("XMP", xmp);
            var subjects = MetadataTool.extractSubjects(json);
            assertThat(subjects).containsExactly("singleKeyword");
        }

        @Test
        void returnsEmpty_whenAbsent() {
            assertThat(MetadataTool.extractSubjects(new JSONObject())).isEmpty();
        }

        @Test
        void deduplicates() {
            var json = new JSONObject();
            var xmp = new JSONObject();
            xmp.put("Subject", new org.json.JSONArray(List.of("tag", "tag", "unique")));
            json.put("XMP", xmp);
            var subjects = MetadataTool.extractSubjects(json);
            assertThat(subjects).hasSize(2).contains("tag", "unique");
        }
    }

    // ------------------------------------------------------------------
    // extractRightsHolder / extractRightsTerms / extractRightsUrl
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Rights extraction")
    class RightsExtraction {

        @Test
        void extractRightsHolder_fromDcRights() {
            var json = new JSONObject();
            var dcXmp = new JSONObject();
            dcXmp.put("Rights", "© 2024 Test Author");
            json.put("XMP-dc", dcXmp);
            assertThat(MetadataTool.extractRightsHolder(json)).isEqualTo("© 2024 Test Author");
        }

        @Test
        void extractRightsHolder_returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractRightsHolder(new JSONObject()));
        }

        @Test
        void extractRightsTerms_fromUsageTerms() {
            var json = new JSONObject();
            var rights = new JSONObject();
            rights.put("UsageTerms", "All rights reserved");
            json.put("XMP-xmpRights", rights);
            assertThat(MetadataTool.extractRightsTerms(json)).isEqualTo("All rights reserved");
        }

        @Test
        void extractRightsTerms_returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractRightsTerms(new JSONObject()));
        }

        @Test
        void extractRightsUrl_fromWebStatement() {
            var json = new JSONObject();
            var rights = new JSONObject();
            rights.put("WebStatement", "https://example.com/license");
            json.put("XMP-xmpRights", rights);
            assertThat(MetadataTool.extractRightsUrl(json)).isEqualTo("https://example.com/license");
        }

        @Test
        void extractRightsUrl_returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractRightsUrl(new JSONObject()));
        }
    }

    // ------------------------------------------------------------------
    // extractCreator* methods
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Creator extraction")
    class CreatorExtraction {

        @Test
        void extractCreatorName_fromIFD0Artist() {
            var json = new JSONObject();
            var ifd0 = new JSONObject();
            ifd0.put("Artist", "Jane Doe");
            json.put("IFD0", ifd0);
            assertThat(MetadataTool.extractCreatorName(json)).isEqualTo("Jane Doe");
        }

        @Test
        void extractCreatorName_returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractCreatorName(new JSONObject()));
        }

        @Test
        void extractCreatorEmail_fromIptcCore() {
            var json = new JSONObject();
            var iptcCore = new JSONObject();
            iptcCore.put("CreatorWorkEmail", "jane@example.com");
            json.put("XMP-iptcCore", iptcCore);
            assertThat(MetadataTool.extractCreatorEmail(json)).isEqualTo("jane@example.com");
        }

        @Test
        void extractCreatorEmail_returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractCreatorEmail(new JSONObject()));
        }

        @Test
        void extractCreatorCountry_fromIptcCore() {
            var json = new JSONObject();
            var iptcCore = new JSONObject();
            iptcCore.put("CreatorCountry", "FI");
            json.put("XMP-iptcCore", iptcCore);
            assertThat(MetadataTool.extractCreatorCountry(json)).isEqualTo("FI");
        }

        @Test
        void extractCreatorCountry_returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractCreatorCountry(new JSONObject()));
        }

        @Test
        void extractCreatorUrl_fromIptcCore() {
            var json = new JSONObject();
            var iptcCore = new JSONObject();
            iptcCore.put("CreatorWorkURL", "https://example.com");
            json.put("XMP-iptcCore", iptcCore);
            assertThat(MetadataTool.extractCreatorUrl(json)).isEqualTo("https://example.com");
        }

        @Test
        void extractCreatorUrl_returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractCreatorUrl(new JSONObject()));
        }
    }

    // ------------------------------------------------------------------
    // extractLabel
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractLabel")
    class ExtractLabel {

        @Test
        void returnsLabelFromXmp() {
            var json = new JSONObject();
            var xmpXmp = new JSONObject();
            xmpXmp.put("Label", "Red");
            json.put("XMP-xmp", xmpXmp);
            assertThat(MetadataTool.extractLabel(json)).isEqualTo("Red");
        }

        @Test
        void returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractLabel(new JSONObject()));
        }
    }

    // ------------------------------------------------------------------
    // extractGpsTime
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractGpsTime")
    class ExtractGpsTime {

        @Test
        void returnsGpsdatetime_fromComposite() {
            var json = new JSONObject();
            var composite = new JSONObject();
            composite.put("GPSDateTime", "2023:06:15 10:20:30Z");
            json.put("Composite", composite);
            var result = MetadataTool.extractGpsTime(json);
            assertNotNull(result);
        }

        @Test
        void returnsNull_fromMalformedDateTime() {
            var json = new JSONObject();
            var composite = new JSONObject();
            composite.put("GPSDateTime", "not-a-date");
            json.put("Composite", composite);
            assertNull(MetadataTool.extractGpsTime(json));
        }

        @Test
        void combinesDateAndTimeStamp() {
            var json = new JSONObject();
            var gps = new JSONObject();
            gps.put("GPSTimeStamp", "10:20:30");
            gps.put("GPSDateStamp", "2023:06:15");
            json.put("GPS", gps);
            var result = MetadataTool.extractGpsTime(json);
            assertNotNull(result);
        }

        @Test
        void returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractGpsTime(new JSONObject()));
        }
    }

    // ------------------------------------------------------------------
    // extractImageResolution
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractImageResolution")
    class ExtractImageResolution {

        @Test
        void returnsResolution_fromSubIFD() {
            var json = new JSONObject();
            var subIFD = new JSONObject();
            subIFD.put("ImageWidth", 1920);
            subIFD.put("ImageHeight", 1080);
            json.put("SUBIFD", subIFD);
            var dim = MetadataTool.extractImageResolution(json);
            assertNotNull(dim);
            assertThat(dim.width).isEqualTo(1920);
            assertThat(dim.height).isEqualTo(1080);
        }

        @Test
        void returnsResolution_fromComposite() {
            // Composite section uses "ImageSize" field containing "WIDTHxHEIGHT" string
            var json = new JSONObject();
            var composite = new JSONObject();
            composite.put("ImageSize", "800x600");
            json.put("Composite", composite);
            var dim = MetadataTool.extractImageResolution(json);
            assertNotNull(dim);
            assertThat(dim.width).isEqualTo(800);
            assertThat(dim.height).isEqualTo(600);
        }

        @Test
        void returnsNull_whenAbsent() {
            assertNull(MetadataTool.extractImageResolution(new JSONObject()));
        }
    }

    // ------------------------------------------------------------------
    // extractImageColorDepth
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractImageColorDepth")
    class ExtractImageColorDepth {

        @Test
        void returnsColorDepth_fromSUBIFD() {
            var json = new JSONObject();
            var subIFD = new JSONObject();
            subIFD.put("BitsPerSample", 8);
            json.put("SUBIFD", subIFD);
            assertThat(MetadataTool.extractImageColorDepth(json)).isEqualTo(8);
        }

        @Test
        void returnsColorDepth_fromIFD0() {
            // extractImageColorDepth searches SUBIFD and IFD0, not a "File" section
            var json = new JSONObject();
            var ifd0 = new JSONObject();
            ifd0.put("BitsPerSample", 24);
            json.put("IFD0", ifd0);
            assertThat(MetadataTool.extractImageColorDepth(json)).isEqualTo(24);
        }

        @Test
        void returnsDefaultEight_whenAbsent() {
            // Default when no BitsPerSample metadata is found is 8
            assertThat(MetadataTool.extractImageColorDepth(new JSONObject())).isEqualTo(8);
        }

        @Test
        void handlesStringValue() {
            var json = new JSONObject();
            var subIFD = new JSONObject();
            subIFD.put("BitsPerSample", "16");
            json.put("SUBIFD", subIFD);
            // Should parse string "16" as integer
            assertThat(MetadataTool.extractImageColorDepth(json)).isEqualTo(16);
        }
    }

    // ------------------------------------------------------------------
    // extractImageDpi
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("extractImageDpi")
    class ExtractImageDpi {

        @Test
        void returnsDpi_fromSubIFD() {
            // extractImageDpi only looks in SUBIFD, not IFD0
            var json = new JSONObject();
            var subIFD = new JSONObject();
            subIFD.put("XResolution", 300);
            json.put("SUBIFD", subIFD);
            assertThat(MetadataTool.extractImageDpi(json)).isEqualTo(300);
        }

        @Test
        void returnsDpi_fromSubIFD_72() {
            var json = new JSONObject();
            var subIFD = new JSONObject();
            subIFD.put("XResolution", 72);
            json.put("SUBIFD", subIFD);
            assertThat(MetadataTool.extractImageDpi(json)).isEqualTo(72);
        }

        @Test
        void returnsDefaultSeventyTwo_whenAbsent() {
            // Default when no XResolution is found in SUBIFD is 72
            assertThat(MetadataTool.extractImageDpi(new JSONObject())).isEqualTo(72);
        }
    }

    // ------------------------------------------------------------------
    // collectStandardMetadataAsJson
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("collectStandardMetadataAsJson")
    class CollectStandardMetadataAsJson {

        @Test
        void emptyMetadataList_returnsJsonArray() {
            var result = MetadataTool.collectStandardMetadataAsJson(List.of(), null);
            assertNotNull(result);
            assertThat(result).startsWith("[");
        }

        @Test
        void nullMetadataList_returnsJsonArray() {
            var result = MetadataTool.collectStandardMetadataAsJson(null, null);
            assertNotNull(result);
            assertThat(result).startsWith("[");
        }

        @Test
        void withFileEntityRightsAndCreator_includesInOutput() {
            var mockFile = Mockito.mock(FileEntity.class);
            Mockito.when(mockFile.getCreatorName()).thenReturn("Test Creator");
            Mockito.when(mockFile.getRightsHolder()).thenReturn("© 2024");
            Mockito.when(mockFile.getRightsUrl()).thenReturn("https://license.example.com");
            Mockito.when(mockFile.getRightsTerms()).thenReturn("All rights reserved");
            Mockito.when(mockFile.getCreatorEmail()).thenReturn("creator@example.com");
            Mockito.when(mockFile.getCreatorCountry()).thenReturn("FI");
            Mockito.when(mockFile.getCreatorUrl()).thenReturn("https://creator.example.com");

            var result = MetadataTool.collectStandardMetadataAsJson(List.of(), mockFile);
            assertThat(result).contains("Test Creator");
            assertThat(result).contains("© 2024");
        }

        @Test
        void withMetadataEntities_extractsFields() {
            var mockFile = Mockito.mock(FileEntity.class);
            Mockito.when(mockFile.getCreatorName()).thenReturn(null);
            Mockito.when(mockFile.getRightsHolder()).thenReturn(null);
            Mockito.when(mockFile.getRightsUrl()).thenReturn(null);
            Mockito.when(mockFile.getRightsTerms()).thenReturn(null);
            Mockito.when(mockFile.getCreatorEmail()).thenReturn(null);
            Mockito.when(mockFile.getCreatorCountry()).thenReturn(null);
            Mockito.when(mockFile.getCreatorUrl()).thenReturn(null);

            var me1 = MetadataEntity.builder()
                                    .metadataGroup("IFD0")
                                    .metadataKey("Artist")
                                    .metadataValue("John Smith")
                                    .build();
            var me2 = MetadataEntity.builder()
                                    .metadataGroup("ExifIFD")
                                    .metadataKey("FNumber")
                                    .metadataValue("2.8")
                                    .build();
            var me3 = MetadataEntity.builder()
                                    .metadataGroup("ExifIFD")
                                    .metadataKey("ExposureTime")
                                    .metadataValue("1/200")
                                    .build();
            var me4 = MetadataEntity.builder()
                                    .metadataGroup("IFD0")
                                    .metadataKey("Make")
                                    .metadataValue("Canon")
                                    .build();

            var result = MetadataTool.collectStandardMetadataAsJson(List.of(me1, me2, me3, me4), mockFile);
            assertThat(result).contains("John Smith");
            assertThat(result).contains("Canon");
        }

        @Test
        void withIsoAndBitsPerSample_parsesNumeric() {
            var mockFile = Mockito.mock(FileEntity.class);
            Mockito.when(mockFile.getCreatorName()).thenReturn(null);
            Mockito.when(mockFile.getRightsHolder()).thenReturn(null);
            Mockito.when(mockFile.getRightsUrl()).thenReturn(null);
            Mockito.when(mockFile.getRightsTerms()).thenReturn(null);
            Mockito.when(mockFile.getCreatorEmail()).thenReturn(null);
            Mockito.when(mockFile.getCreatorCountry()).thenReturn(null);
            Mockito.when(mockFile.getCreatorUrl()).thenReturn(null);

            var isoMe = MetadataEntity.builder()
                                      .metadataGroup("ExifIFD")
                                      .metadataKey("ISO")
                                      .metadataValue("400")
                                      .build();
            var bpsMe = MetadataEntity.builder()
                                      .metadataGroup("File")
                                      .metadataKey("BitsPerSample")
                                      .metadataValue("8")
                                      .build();
            var megapixelsMe = MetadataEntity.builder()
                                             .metadataGroup("Composite")
                                             .metadataKey("Megapixels")
                                             .metadataValue("24.2")
                                             .build();

            var result = MetadataTool.collectStandardMetadataAsJson(List.of(isoMe, bpsMe, megapixelsMe), mockFile);
            assertThat(result).contains("400");
            assertThat(result).contains("8");
        }

        @Test
        void withImageSize_fromWidthAndHeight() {
            var mockFile = Mockito.mock(FileEntity.class);
            Mockito.when(mockFile.getCreatorName()).thenReturn(null);
            Mockito.when(mockFile.getRightsHolder()).thenReturn(null);
            Mockito.when(mockFile.getRightsUrl()).thenReturn(null);
            Mockito.when(mockFile.getRightsTerms()).thenReturn(null);
            Mockito.when(mockFile.getCreatorEmail()).thenReturn(null);
            Mockito.when(mockFile.getCreatorCountry()).thenReturn(null);
            Mockito.when(mockFile.getCreatorUrl()).thenReturn(null);

            var widthMe = MetadataEntity.builder()
                                        .metadataGroup("File")
                                        .metadataKey("ImageWidth")
                                        .metadataValue("1920")
                                        .build();
            var heightMe = MetadataEntity.builder()
                                         .metadataGroup("File")
                                         .metadataKey("ImageHeight")
                                         .metadataValue("1080")
                                         .build();

            var result = MetadataTool.collectStandardMetadataAsJson(List.of(widthMe, heightMe), mockFile);
            assertThat(result).contains("1920x1080");
        }
    }

    // ------------------------------------------------------------------
    // metadataToJsonObject
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("metadataToJsonObject")
    class MetadataToJsonObject {

        @Test
        void parsesValidJson() {
            var json = "[{\"File\":{\"MIMEType\":\"image/jpeg\"}}]";
            var result = MetadataTool.metadataToJsonObject(json);
            assertNotNull(result);
        }

        @Test
        void throwsForNullInput() {
            // metadataToJsonObject passes null directly to new JSONArray() which throws NPE
            assertThrows(NullPointerException.class, () -> MetadataTool.metadataToJsonObject(null));
        }

        @Test
        void throwsForBlankInput() {
            // blank string is not valid JSON array → JSONException
            assertThrows(Exception.class, () -> MetadataTool.metadataToJsonObject("   "));
        }

        @Test
        void throwsForInvalidJson() {
            assertThrows(Exception.class, () -> MetadataTool.metadataToJsonObject("not-json"));
        }

        @Test
        void returnsNull_forEmptyArray() {
            assertNull(MetadataTool.metadataToJsonObject("[]"));
        }
    }
}
