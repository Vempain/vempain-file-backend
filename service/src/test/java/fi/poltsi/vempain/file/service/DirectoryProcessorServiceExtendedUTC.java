package fi.poltsi.vempain.file.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.exception.VempainRuntimeException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.common.metadata.ExifToolService;
import fi.poltsi.vempain.file.api.response.ExportFileResponse;
import fi.poltsi.vempain.file.entity.ArchiveFileEntity;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.BinaryFileEntity;
import fi.poltsi.vempain.file.entity.DataFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.ExecutableFileEntity;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.FontFileEntity;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import fi.poltsi.vempain.file.entity.IconFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.InteractiveFileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import fi.poltsi.vempain.file.entity.MusicFileEntity;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.entity.ThumbFileEntity;
import fi.poltsi.vempain.file.entity.VectorFileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.GpsLocationRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.tools.FileMetadataTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for the file type and metadata branches of {@link DirectoryProcessorService}. ExifTool is replaced by a mocked {@code ExifToolService} that
 * answers the file based extractors from a per-test table and runs every JSON based extractor for real, so that each of the 14 typed entities, the GPS
 * handling, the refresh of an existing file and the export directory scan can be driven without ExifTool being able to read the generated files.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DirectoryProcessorServiceExtendedUTC {

    @Mock
    private FileGroupRepository fileGroupRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private MetadataRepository metadataRepository;
    @Mock
    private FileTagRepository fileTagRepository;
    @Mock
    private ExportFileRepository exportFileRepository;
    @Mock
    private AclService aclService;
    @Mock
    private ExportedFilesService exportedFilesService;
    @Mock
    private FileProcessingQueueService fileProcessingQueueService;
    @Mock
    private GpsLocationRepository gpsLocationRepository;
    @Mock
    private ExifToolService exifToolService;

    @TempDir
    Path tempDir;

    private DirectoryProcessorService service;
    private FileGroupEntity group;

    @BeforeEach
    void setUp() throws Exception {
        service = new DirectoryProcessorService(fileGroupRepository, fileRepository, tagRepository, metadataRepository, fileTagRepository, exportFileRepository,
                aclService, exportedFilesService, fileProcessingQueueService, gpsLocationRepository, exifToolService);
        ReflectionTestUtils.setField(service, "originalRootDirectory", tempDir.toString());
        ReflectionTestUtils.setField(service, "exportRootDirectory", tempDir.toString());
        group = FileGroupEntity.builder()
            .id(1L)
            .path("/")
            .groupName("root")
            .description("")
            .build();
        authenticateAs(1L);
        when(aclService.createUniqueAcl(eq(1L), any(), eq(true), eq(true), eq(true), eq(true))).thenReturn(Acl.builder()
            .aclId(5L)
            .build());
        when(fileRepository.findByFilePathAndFilename(any(), any())).thenReturn(Optional.empty());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileGroupRepository.save(any(FileGroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByTagName(anyString())).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(invocation -> {
            var tag = invocation.getArgument(0, TagEntity.class);
            tag.setId((long) tag.getTagName()
                .hashCode());
            return tag;
        });
        when(fileTagRepository.findByFile(any())).thenReturn(List.of());
        when(metadataRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(metadataRepository.findByFile(any())).thenReturn(List.of());
        when(gpsLocationRepository.findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(gpsLocationRepository.save(any(GpsLocationEntity.class))).thenAnswer(invocation -> {
            var gps = invocation.getArgument(0, GpsLocationEntity.class);
            gps.setId(77L);
            return gps;
        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(long userId) {
        var principal = new UserDetailsImpl(userId, "user", "User", "user@nohost.nodomain", "Disabled", Set.of(), List.of());
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /**
     * Stubs the shared ExifTool read with the JSON and the FFmpeg codec probe with "h264"; the per-type fields the scanner reads from the JSON come from
     * {@link #probe()}. Closing the result ends the codec stub.
     */
    private Stub mockTool(String metadataJson) throws IOException {
        doReturn(metadataJson).when(exifToolService)
            .readMetadataJson(any());
        var codec = mockStatic(FileMetadataTool.class, invocation -> {
            if ("extractVideoCodec".equals(invocation.getMethod()
                .getName())) {
                return "h264";
            }
            return invocation.callRealMethod();
        });
        return codec::close;
    }

    /**
     * Stubs the shared ExifTool read to fail.
     */
    private Stub mockToolFailure() throws IOException {
        doThrow(new IOException("exiftool missing")).when(exifToolService)
            .readMetadataJson(any());
        return () -> {};
    }

    /** A stub of the tool that is closed without checked exceptions, so that the helpers can be used in try-with-resources. */
    private interface Stub extends AutoCloseable {

        @Override
        void close();
    }

    /**
     * The tag values of every file type the scanner reads from the grouped JSON, in one group of their own.
     */
    private static JSONObject probe() {
        return new JSONObject().put("ImageWidth", 16)
            .put("ImageHeight", 9)
            .put("VideoFrameRate", 25.0)
            .put("Duration", "0:00:42")
            .put("AudioBitrate", 128)
            .put("AudioSampleRate", 44100)
            .put("AudioCodec", "pcm")
            .put("AudioChannels", 2)
            .put("PageCount", 3)
            .put("FileType", "PDF")
            .put("Layers", 4)
            .put("Scalable", true)
            .put("FontFamily", "Lato")
            .put("FontWeight", "Medium")
            .put("FontStyle", "Regular")
            .put("Compression", "Deflated")
            .put("UncompressedSize", 600)
            .put("ContentCount", 2)
            .put("Encrypted", false);
    }

    private static String metadataJson(String mimetype, JSONObject... groups) {
        var object = new JSONObject();
        object.put("SourceFile", "/x");
        if (mimetype != null) {
            object.put("File", new JSONObject().put("MIMEType", mimetype));
        }
        for (var group : groups) {
            for (var key : group.keySet()) {
                object.put(key, group.get(key));
            }
        }
        object.put("Probe", probe());
        return new JSONArray().put(object)
            .toString();
    }

    private static JSONObject group(String name, JSONObject content) {
        return new JSONObject().put(name, content);
    }

    private File write(String name) throws IOException {
        var file = tempDir.resolve(name);
        Files.writeString(file, "content of " + name);
        return file.toFile();
    }

    private FileEntity processed(File file, String metadataJson) throws IOException {
        try (var ignored = mockTool(metadataJson)) {
            assertThat(service.processOriginalFile(file, group)).isTrue();
        }
        var captor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepository).save(captor.capture());
        var entity = captor.getValue();
        assertThat(group.getFiles()).contains(entity);
        assertThat(entity.getFileGroups()).contains(group);
        assertThat(entity.getAclId()).isEqualTo(5L);
        assertThat(entity.getCreator()).isEqualTo(1L);
        assertThat(entity.getFilename()).isEqualTo(file.getName());
        assertThat(entity.getFilePath()).isEqualTo("/");
        verify(fileProcessingQueueService).enqueueIfNotExported(entity);
        return entity;
    }

    @Nested
    @DisplayName("processOriginalFile stores one typed entity per MIME type")
    class TypedEntities {

        @Test
        void archive() throws Exception {
            var entity = processed(write("a.zip"), metadataJson(null));
            assertThat(entity).isInstanceOf(ArchiveFileEntity.class);
            assertThat(entity.getFileType()).isEqualTo(FileTypeEnum.ARCHIVE);
            assertThat(entity.getExternalFileId()).startsWith("ARCHIVE")
                .doesNotContain("-");
            var archive = (ArchiveFileEntity) entity;
            assertThat(archive.getCompressionMethod()).isEqualTo("Deflated");
            assertThat(archive.getUncompressedSize()).isEqualTo(600L);
            assertThat(archive.getContentCount()).isEqualTo(2);
            assertThat(archive.getIsEncrypted()).isFalse();
        }

        @Test
        void audioWithoutMusicMetadata() throws Exception {
            var entity = processed(write("a.wav"), metadataJson(null));
            assertThat(entity).isInstanceOf(AudioFileEntity.class)
                .isNotInstanceOf(MusicFileEntity.class);
            var audio = (AudioFileEntity) entity;
            assertThat(audio.getDuration()).isEqualTo(Duration.ofSeconds(42));
            assertThat(audio.getBitRate()).isEqualTo(128);
            assertThat(audio.getSampleRate()).isEqualTo(44100);
            assertThat(audio.getCodec()).isEqualTo("pcm");
            assertThat(audio.getChannels()).isEqualTo(2);
        }

        @Test
        void audioWithMusicMetadataBecomesMusic() throws Exception {
            var id3 = new JSONObject().put("Artist", "Band")
                .put("Album", "Album")
                .put("Title", "Song")
                .put("Track", "3/12")
                .put("Year", 1999)
                .put("Genre", "Rock")
                .put("Comment", "Live");
            var entity = processed(write("a.mp3"), metadataJson(null, group("ID3v2_3", id3)));
            assertThat(entity).isInstanceOf(MusicFileEntity.class);
            assertThat(entity.getFileType()).isEqualTo(FileTypeEnum.MUSIC);
            var music = (MusicFileEntity) entity;
            assertThat(music.getArtist()).isEqualTo("Band");
            assertThat(music.getAlbumArtist()).isEqualTo("Band");
            assertThat(music.getAlbum()).isEqualTo("Album");
            assertThat(music.getTrackName()).isEqualTo("Song");
            assertThat(music.getTrackNumber()).isEqualTo(3);
            assertThat(music.getTrackTotal()).isEqualTo(12);
            assertThat(music.getYear()).isEqualTo(1999);
            assertThat(music.getGenre()).isEqualTo("Rock");
            assertThat(music.getDescription()).isEqualTo("Live");
            assertThat(music.getDuration()).isEqualTo(Duration.ofSeconds(42));
        }

        @Test
        void binaryTakesTheSoftwareFromTheLabelOrTheFilename() throws Exception {
            var labelled = processed(write("tool.bin"), metadataJson(null, group("XMP-xmp", new JSONObject().put("Label", "Tool 12.3"))));
            assertThat(labelled).isInstanceOf(BinaryFileEntity.class);
            assertThat(((BinaryFileEntity) labelled).getSoftwareName()).isEqualTo("Tool 12.3");
            assertThat(((BinaryFileEntity) labelled).getSoftwareMajorVersion()).isEqualTo(12);

            try (var ignored = mockTool(metadataJson(null))) {
                service.processOriginalFile(write("firmware-7.bin"), group);
                service.processOriginalFile(write("plain.bin"), group);
            }
            var captor = ArgumentCaptor.forClass(FileEntity.class);
            verify(fileRepository, org.mockito.Mockito.times(3)).save(captor.capture());
            assertThat(((BinaryFileEntity) captor.getAllValues()
                .get(1)).getSoftwareMajorVersion()).isEqualTo(7);
            assertThat(((BinaryFileEntity) captor.getAllValues()
                .get(2)).getSoftwareMajorVersion()).isNull();
        }

        @Test
        void dataStructureFollowsTheMimeType() throws Exception {
            try (var ignored = mockTool(metadataJson(null))) {
                service.processOriginalFile(write("a.json"), group);
                service.processOriginalFile(write("a.xml"), group);
                service.processOriginalFile(write("a.csv"), group);
                service.processOriginalFile(write("a.yaml"), group);
            }
            var captor = ArgumentCaptor.forClass(FileEntity.class);
            verify(fileRepository, org.mockito.Mockito.times(4)).save(captor.capture());
            assertThat(captor.getAllValues()).allSatisfy(entity -> assertThat(entity).isInstanceOf(DataFileEntity.class));
            assertThat(captor.getAllValues()
                .stream()
                .map(entity -> ((DataFileEntity) entity).getDataStructure())
                .toList()).containsExactly("JSON", "XML", "CSV", "YAML");
        }

        @Test
        void document() throws Exception {
            var entity = processed(write("a.pdf"), metadataJson(null));
            assertThat(entity).isInstanceOf(DocumentFileEntity.class);
            assertThat(((DocumentFileEntity) entity).getPageCount()).isEqualTo(3);
            assertThat(((DocumentFileEntity) entity).getFormat()).isEqualTo("PDF");
        }

        @Test
        void executableDetectsOperatingSystemsAndScripts() throws Exception {
            try (var ignored = mockTool(metadataJson(null))) {
                service.processOriginalFile(write("run.sh"), group);
                service.processOriginalFile(write("lib.jar"), group);
                service.processOriginalFile(write("app.apk"), group);
                service.processOriginalFile(write("setup.msi"), group);
            }
            try (var ignored = mockTool(metadataJson("application/x-executable"))) {
                service.processOriginalFile(write("installer.run"), group);
                service.processOriginalFile(write("tool.app"), group);
            }
            try (var ignored = mockTool(metadataJson("application/x-bat"))) {
                service.processOriginalFile(write("tool.cmd"), group);
            }
            var captor = ArgumentCaptor.forClass(FileEntity.class);
            verify(fileRepository, org.mockito.Mockito.times(7)).save(captor.capture());
            var executables = captor.getAllValues()
                .stream()
                .map(entity -> (ExecutableFileEntity) entity)
                .toList();
            assertThat(executables.get(0)
                .getOperatingSystems()).containsExactly("OTHER");
            assertThat(executables.get(0)
                .isScript()).isTrue();
            assertThat(executables.get(1)
                .getOperatingSystems()).containsExactly("JVM");
            assertThat(executables.get(1)
                .isScript()).isFalse();
            assertThat(executables.get(2)
                .getOperatingSystems()).containsExactly("ANDROID");
            assertThat(executables.get(3)
                .getOperatingSystems()).containsExactly("WINDOWS");
            assertThat(executables.get(4)
                .getOperatingSystems()).containsExactly("LINUX");
            assertThat(executables.get(5)
                .getOperatingSystems()).containsExactlyInAnyOrder("LINUX", "MACOS");
            assertThat(executables.get(6)
                .getOperatingSystems()).containsExactly("WINDOWS");
            assertThat(executables.get(6)
                .isScript()).isTrue();
        }

        @Test
        void font() throws Exception {
            var entity = processed(write("a.ttf"), metadataJson(null));
            assertThat(entity).isInstanceOf(FontFileEntity.class);
            var font = (FontFileEntity) entity;
            assertThat(font.getFontFamily()).isEqualTo("Lato");
            assertThat(font.getWeight()).isEqualTo("Medium");
            assertThat(font.getStyle()).isEqualTo("Regular");
        }

        @Test
        void icon() throws Exception {
            var entity = processed(write("a.ico"), metadataJson(null));
            assertThat(entity).isInstanceOf(IconFileEntity.class);
            var icon = (IconFileEntity) entity;
            assertThat(icon.getWidth()).isEqualTo(16);
            assertThat(icon.getHeight()).isEqualTo(9);
            assertThat(icon.getIsScalable()).isTrue();
        }

        @Test
        void imageWithResolutionDepthDpiAndLabel() throws Exception {
            var json = metadataJson(null, group("Composite", new JSONObject().put("ImageSize", "640x480")),
                    group("SUBIFD", new JSONObject().put("BitsPerSample", "8 8 8")
                        .put("XResolution", 300)),
                    group("XMP-xmp", new JSONObject().put("Label", "Blue")));
            var entity = processed(write("a.jpg"), json);
            assertThat(entity).isInstanceOf(ImageFileEntity.class);
            assertThat(entity.getExternalFileId()).startsWith("IMAGE-");
            var image = (ImageFileEntity) entity;
            assertThat(image.getWidth()).isEqualTo(640);
            assertThat(image.getHeight()).isEqualTo(480);
            assertThat(image.getColorDepth()).isEqualTo(24);
            assertThat(image.getDpi()).isEqualTo(300);
            assertThat(image.getGroupLabel()).isEqualTo("Blue");
        }

        @Test
        void imageWithoutResolutionKeepsTheDefaults() throws Exception {
            var entity = processed(write("a.png"), metadataJson(null));
            var image = (ImageFileEntity) entity;
            assertThat(image.getWidth()).isZero();
            assertThat(image.getHeight()).isZero();
            assertThat(image.getColorDepth()).isEqualTo(8);
            assertThat(image.getDpi()).isEqualTo(72);
        }

        @Test
        void interactiveTechnologyFollowsTheMimeType() throws Exception {
            try (var ignored = mockTool(metadataJson(null))) {
                service.processOriginalFile(write("movie.dir"), group);
            }
            try (var ignored = mockTool(metadataJson("application/x-shockwave-flash"))) {
                service.processOriginalFile(write("movie.flash"), group);
            }
            var captor = ArgumentCaptor.forClass(FileEntity.class);
            verify(fileRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            assertThat(((InteractiveFileEntity) captor.getAllValues()
                .get(0)).getTechnology()).isEqualTo("SHOCKWAVE");
            assertThat(((InteractiveFileEntity) captor.getAllValues()
                .get(1)).getTechnology()).isEqualTo("FLASH");
        }

        @Test
        void thumbLinksToTheOriginalThroughTheDocumentId() throws Exception {
            var target = new ImageFileEntity();
            target.setId(9L);
            when(fileRepository.findByOriginalDocumentId("doc-9")).thenReturn(null, target);
            var json = metadataJson("image/x-thumbnail", group("XMP-xmpMM", new JSONObject().put("DerivedFromOriginalDocumentID", "doc-9")));

            var entity = processed(write("a.thumb"), json);

            assertThat(entity).isInstanceOf(ThumbFileEntity.class);
            var thumb = (ThumbFileEntity) entity;
            assertThat(thumb.getRelationType()).isEqualTo("thumbnail");
            assertThat(thumb.getTargetFile()).isSameAs(target);
            assertThat(thumb.getOriginalDocumentId()).isEqualTo("doc-9");
        }

        @Test
        void thumbWithoutDocumentIdOrTargetStaysUnlinked() throws Exception {
            try (var ignored = mockTool(metadataJson("image/x-thumbnail"))) {
                service.processOriginalFile(write("b.thumb"), group);
            }
            when(fileRepository.findByOriginalDocumentId("doc-x")).thenReturn(null);
            try (var ignored = mockTool(metadataJson("image/x-thumbnail", group("XMP-xmpMM", new JSONObject().put("InstanceID", "doc-x"))))) {
                service.processOriginalFile(write("c.thumb"), group);
            }
            var captor = ArgumentCaptor.forClass(FileEntity.class);
            verify(fileRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            assertThat(captor.getAllValues()).allSatisfy(entity -> assertThat(((ThumbFileEntity) entity).getTargetFile()).isNull());
        }

        @Test
        void vector() throws Exception {
            var entity = processed(write("a.svg"), metadataJson(null));
            assertThat(entity).isInstanceOf(VectorFileEntity.class);
            var vector = (VectorFileEntity) entity;
            assertThat(vector.getWidth()).isEqualTo(16);
            assertThat(vector.getHeight()).isEqualTo(9);
            assertThat(vector.getLayersCount()).isEqualTo(4);
        }

        @Test
        void video() throws Exception {
            var entity = processed(write("a.mp4"), metadataJson(null));
            assertThat(entity).isInstanceOf(VideoFileEntity.class);
            var video = (VideoFileEntity) entity;
            assertThat(video.getWidth()).isEqualTo(16);
            assertThat(video.getHeight()).isEqualTo(9);
            assertThat(video.getFrameRate()).isEqualTo(25.0);
            assertThat(video.getDuration()).isEqualTo(Duration.ofSeconds(42));
            assertThat(video.getCodec()).isEqualTo("h264");
        }

        @Test
        void unsupportedMimeTypeIsReportedAsFailure() throws Exception {
            try (var ignored = mockTool(metadataJson("application/x-unknown-thing"))) {
                assertThat(service.processOriginalFile(write("a.swf"), group)).isFalse();
            }
            verify(fileRepository, never()).save(any(FileEntity.class));
        }
    }

    @Nested
    @DisplayName("processOriginalFile metadata, tags and failures")
    class MetadataAndFailures {

        @Test
        void storesTagsMetadataRightsCreatorAndDatesFromTheJson() throws Exception {
            var json = metadataJson(null, group("XMP-dc", new JSONObject().put("Subject", new JSONArray().put("alpha")
                .put(" ")
                .put("beta"))
                .put("Description", "A caption")
                .put("Rights", "Holder")), group("ExifIFD",
                        new JSONObject().put("DateTimeOriginal", "2024:01:02 03:04:05")
                            .put("SubSecTimeOriginal", 12)),
                    group("XMP-xmpMM", new JSONObject().put("OriginalDocumentID", "doc-1")), group("XMP-xmpRights", new JSONObject().put("UsageTerms", "Terms")
                        .put("WebStatement", "http://w")),
                    group("IFD0", new JSONObject().put("Artist", "Artist")), group("XMP-iptcCore", new JSONObject().put("CreatorWorkEmail", "e@x")
                        .put("CreatorCountry", "FI")
                        .put("CreatorWorkURL", "http://c")));
            var existingTag = TagEntity.builder()
                .id(3L)
                .tagName("beta")
                .build();
            when(tagRepository.findByTagName("beta")).thenReturn(Optional.of(existingTag));
            when(fileRepository.findByOriginalDocumentId("doc-1")).thenReturn(null);

            var entity = processed(write("tagged.jpg"), json);

            assertThat(entity.getDescription()).isEqualTo("A caption");
            assertThat(entity.getRightsHolder()).isEqualTo("Holder");
            assertThat(entity.getRightsTerms()).isEqualTo("Terms");
            assertThat(entity.getRightsUrl()).isEqualTo("http://w");
            assertThat(entity.getCreatorName()).isEqualTo("Artist");
            assertThat(entity.getCreatorEmail()).isEqualTo("e@x");
            assertThat(entity.getCreatorCountry()).isEqualTo("FI");
            assertThat(entity.getCreatorUrl()).isEqualTo("http://c");
            assertThat(entity.getOriginalDocumentId()).isEqualTo("doc-1");
            assertThat(entity.getOriginalSecondFraction()).isEqualTo(12);
            assertThat(entity.getOriginalDatetime()).isNotNull();
            assertThat(entity.getMetadataRaw()).isEqualTo(json);
            assertThat(entity.getGpsLocation()).isNull();

            verify(tagRepository).lockTagMutations();
            var savedTags = ArgumentCaptor.forClass(TagEntity.class);
            verify(tagRepository).save(savedTags.capture());
            assertThat(savedTags.getValue()
                .getTagName()).isEqualTo("alpha");
            var fileTags = ArgumentCaptor.forClass(FileTag.class);
            verify(fileTagRepository, org.mockito.Mockito.times(2)).save(fileTags.capture());
            assertThat(fileTags.getAllValues()).extracting(fileTag -> fileTag.getTag()
                .getTagName())
                .containsExactly("alpha", "beta");
            ArgumentCaptor<List<MetadataEntity>> metadata = ArgumentCaptor.captor();
            verify(metadataRepository).saveAll(metadata.capture());
            var rows = metadata.getValue();
            assertThat(rows).extracting(MetadataEntity::getMetadataGroup)
                .doesNotContain("SourceFile");
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.getMetadataGroup()).isEqualTo("XMP-dc");
                assertThat(row.getMetadataKey()).isEqualTo("Description");
                assertThat(row.getMetadataValue()).isEqualTo("A caption");
                assertThat(row.getFile()).isSameAs(entity);
            });
        }

        @Test
        void skipsAnAlreadyLinkedTagAndFilesWithoutSubjects() throws Exception {
            var tag = TagEntity.builder()
                .id(3L)
                .tagName("beta")
                .build();
            when(tagRepository.findByTagName("beta")).thenReturn(Optional.of(tag));
            when(fileTagRepository.findByFile(any())).thenReturn(List.of(FileTag.builder()
                .tag(tag)
                .build()));
            try (var ignored = mockTool(metadataJson(null, group("XMP-dc", new JSONObject().put("Subject", "beta"))))) {
                service.processOriginalFile(write("linked.jpg"), group);
            }
            try (var ignored = mockTool(metadataJson(null))) {
                service.processOriginalFile(write("untagged.jpg"), group);
            }
            verify(fileTagRepository, never()).save(any());
        }

        @Test
        void metadataExtractionFailuresAreReportedAsFailures() throws Exception {
            var file = write("broken.jpg");
            try (var ignored = mockToolFailure()) {
                assertThat(service.processOriginalFile(file, group)).isFalse();
            }
            try (var ignored = mockTool("   ")) {
                assertThat(service.processOriginalFile(file, group)).isFalse();
            }
            try (var ignored = mockTool(null)) {
                assertThat(service.processOriginalFile(file, group)).isFalse();
            }
            try (var ignored = mockTool("[]")) {
                assertThat(service.processOriginalFile(file, group)).isFalse();
            }
            verify(fileRepository, never()).save(any(FileEntity.class));
        }

        @Test
        void unknownMimeTypeFromBothProbesIsAFailure() throws Exception {
            try (var ignored = mockTool(metadataJson(null))) {
                assertThat(service.processOriginalFile(write("noidea.xyz123"), group)).isFalse();
            }
            verify(fileRepository, never()).save(any(FileEntity.class));
        }

        @Test
        void missingAuthenticationOrIllegalUserIdPreventsTheEntity() throws Exception {
            var file = write("a.jpg");
            SecurityContextHolder.clearContext();
            try (var ignored = mockTool(metadataJson(null))) {
                assertThat(service.processOriginalFile(file, group)).isFalse();
            }
            authenticateAs(0L);
            try (var ignored = mockTool(metadataJson(null))) {
                assertThat(service.processOriginalFile(file, group)).isFalse();
            }
            verify(fileRepository, never()).save(any(FileEntity.class));
        }

        @Test
        void duplicateOriginalDocumentIdIsAFailure() throws Exception {
            var existing = new ImageFileEntity();
            existing.setFilePath("/old");
            existing.setFilename("old.jpg");
            when(fileRepository.findByOriginalDocumentId("doc-dup")).thenReturn(existing);
            try (var ignored = mockTool(metadataJson(null, group("XMP-xmpMM", new JSONObject().put("DocumentID", "doc-dup"))))) {
                assertThat(service.processOriginalFile(write("dup.jpg"), group)).isFalse();
            }
            verify(fileRepository, never()).save(any(FileEntity.class));
        }

        @Test
        void aclFailureIsARuntimeException() throws Exception {
            when(aclService.createUniqueAcl(any(), any(), eq(true), eq(true), eq(true), eq(true))).thenThrow(new VempainAclException("no acl"));
            var file = write("a.jpg");
            try (var ignored = mockTool(metadataJson(null))) {
                assertThatThrownBy(() -> service.processOriginalFile(file, group)).isInstanceOf(VempainRuntimeException.class);
            }
        }

        @Test
        void unchangedExistingFileIsSkippedAndChangedOneIsReplaced() throws Exception {
            var file = write("a.jpg");
            var existing = new ImageFileEntity();
            existing.setSha256sum(fi.poltsi.vempain.file.tools.FileTool.computeSha256(file));
            when(fileRepository.findByFilePathAndFilename("/", "a.jpg")).thenReturn(Optional.of(existing));
            try (var ignored = mockTool(metadataJson(null))) {
                assertThat(service.processOriginalFile(file, group)).isNull();
            }
            verify(fileRepository, never()).delete(any(FileEntity.class));

            existing.setSha256sum("different");
            try (var ignored = mockTool(metadataJson(null))) {
                assertThat(service.processOriginalFile(file, group)).isTrue();
            }
            verify(fileRepository).delete(existing);
            verify(fileRepository).save(any(FileEntity.class));
        }
    }

    @Nested
    @DisplayName("GPS locations")
    class Gps {

        private String gpsJson(JSONObject location) {
            var gps = new JSONObject().put("GPSLatitudeRef", "North")
                .put("GPSLatitude", "60 deg 10' 12.00\"")
                .put("GPSLongitudeRef", "East")
                .put("GPSLongitude", "24 deg 56' 24.00\"")
                .put("GPSAltitude", "10 m");
            return metadataJson(null, group("GPS", gps), group("XMP-photoshop", location),
                    group("Composite", new JSONObject().put("GPSDateTime", "2024:01:02 03:04:05Z")));
        }

        @Test
        void newCoordinatesArePersisted() throws Exception {
            var entity = processed(write("gps.jpg"), gpsJson(new JSONObject().put("City", "Helsinki")));
            assertThat(entity.getGpsLocation()).isNotNull();
            assertThat(entity.getGpsLocation()
                .getId()).isEqualTo(77L);
            assertThat(entity.getGpsLocation()
                .getLatitude()).isEqualByComparingTo(new BigDecimal("60.17000"));
            assertThat(entity.getGpsLocation()
                .getCity()).isEqualTo("Helsinki");
            assertThat(entity.getGpsTimestamp()).isNotNull();
            verify(gpsLocationRepository).save(any(GpsLocationEntity.class));
        }

        @Test
        void existingCoordinatesAreEnrichedWithMissingLocationFields() throws Exception {
            var existing = new GpsLocationEntity();
            existing.setId(5L);
            when(gpsLocationRepository.findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(any(), eq('N'), any(), eq('E')))
                .thenReturn(Optional.of(existing));
            when(gpsLocationRepository.save(existing)).thenReturn(existing);
            var location = new JSONObject().put("City", "Helsinki")
                .put("State", "Uusimaa")
                .put("Country", "Suomi");
            var json = metadataJson(null, group("GPS", new JSONObject().put("GPSLatitudeRef", "N")
                .put("GPSLatitude", "60 deg 10' 12.00\"")
                .put("GPSLongitudeRef", "E")
                .put("GPSLongitude", "24 deg 56' 24.00\"")), group("XMP-photoshop", location),
                    group("XMP-iptcCore", new JSONObject().put("Location", "Street 1")));

            var entity = processed(write("gps2.jpg"), json);

            assertThat(entity.getGpsLocation()).isSameAs(existing);
            assertThat(existing.getCity()).isEqualTo("Helsinki");
            assertThat(existing.getState()).isEqualTo("Uusimaa");
            assertThat(existing.getCountry()).isEqualTo("Suomi");
            assertThat(existing.getStreet()).isEqualTo("Street 1");
            verify(gpsLocationRepository).save(existing);
        }

        @Test
        void completeExistingCoordinatesAreReusedUnchanged() throws Exception {
            var existing = new GpsLocationEntity();
            existing.setId(6L);
            existing.setCountry("FI");
            existing.setCity("Helsinki");
            existing.setState("Uusimaa");
            existing.setStreet("Street");
            existing.setSubLocation("Sub");
            when(gpsLocationRepository.findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(any(), eq('N'), any(), eq('E')))
                .thenReturn(Optional.of(existing));

            var entity = processed(write("gps3.jpg"), gpsJson(new JSONObject().put("City", "Espoo")
                .put("State", "Other")
                .put("Country", "SE")));

            assertThat(entity.getGpsLocation()).isSameAs(existing);
            assertThat(existing.getCity()).isEqualTo("Helsinki");
            verify(gpsLocationRepository, never()).save(any(GpsLocationEntity.class));
        }

        @Test
        void coordinatesWithoutLongitudeAreNotAssociated() throws Exception {
            var json = metadataJson(null, group("GPS", new JSONObject().put("GPSLatitudeRef", "North")
                .put("GPSLatitude", "60 deg 10' 12.00\"")));
            var entity = processed(write("gps4.jpg"), json);
            assertThat(entity.getGpsLocation()).isNull();
            verify(gpsLocationRepository, never()).save(any(GpsLocationEntity.class));
        }
    }

    @Nested
    @DisplayName("refreshExistingOriginalFile")
    class Refresh {

        private FileEntity stored(FileEntity entity, FileTypeEnum type, String name) {
            entity.setId(20L);
            entity.setFileType(type);
            entity.setFilename(name);
            entity.setFilePath("/");
            entity.setSha256sum("old");
            return entity;
        }

        @Test
        void rejectsMissingArgumentsAndFiles() throws Exception {
            var entity = stored(new ImageFileEntity(), FileTypeEnum.IMAGE, "a.jpg");
            assertThat(service.refreshExistingOriginalFile(null, write("a.jpg"))).isFalse();
            assertThat(service.refreshExistingOriginalFile(entity, null)).isFalse();
            assertThat(service.refreshExistingOriginalFile(entity, tempDir.resolve("missing.jpg")
                .toFile())).isFalse();
        }

        @Test
        void rejectsUnreadableMetadataAndTypeChanges() throws Exception {
            var entity = stored(new ImageFileEntity(), FileTypeEnum.IMAGE, "a.jpg");
            var file = write("a.jpg");
            try (var ignored = mockToolFailure()) {
                assertThat(service.refreshExistingOriginalFile(entity, file)).isFalse();
            }
            try (var ignored = mockTool(" ")) {
                assertThat(service.refreshExistingOriginalFile(entity, file)).isFalse();
            }
            try (var ignored = mockTool("[]")) {
                assertThat(service.refreshExistingOriginalFile(entity, file)).isFalse();
            }
            try (var ignored = mockTool(metadataJson(null))) {
                // no MIME type at all for an unknown extension
                assertThat(service.refreshExistingOriginalFile(entity, write("a.xyz123"))).isFalse();
                // an unknown type
                assertThat(service.refreshExistingOriginalFile(entity, write("a.swf"))).isFalse();
                // the type changed from image to document
                assertThat(service.refreshExistingOriginalFile(entity, write("a.pdf"))).isFalse();
            }
            verify(fileRepository, never()).save(any(FileEntity.class));
        }

        @Test
        void refreshesAnImageWithItsTagsMetadataAndGps() throws Exception {
            var entity = stored(new ImageFileEntity(), FileTypeEnum.IMAGE, "a.jpg");
            var file = write("a.jpg");
            var oldMetadata = List.of(MetadataEntity.builder()
                .file(entity)
                .build());
            var oldFileTags = List.of(FileTag.builder()
                .file(entity)
                .build());
            when(metadataRepository.findByFile(entity)).thenReturn(oldMetadata);
            when(fileTagRepository.findByFile(entity)).thenReturn(oldFileTags)
                .thenReturn(List.of());
            var json = metadataJson(null, group("Composite", new JSONObject().put("ImageSize", "32x24")),
                    group("XMP-dc", new JSONObject().put("Subject", "gamma")
                        .put("Description", "new caption")),
                    group("GPS", new JSONObject().put("GPSLatitudeRef", "N")
                        .put("GPSLatitude", "60 deg 10' 12.00\"")
                        .put("GPSLongitudeRef", "E")
                        .put("GPSLongitude", "24 deg 56' 24.00\"")));

            try (var ignored = mockTool(json)) {
                assertThat(service.refreshExistingOriginalFile(entity, file)).isTrue();
            }

            assertThat(entity.getDescription()).isEqualTo("new caption");
            assertThat(entity.getSha256sum()).isNotEqualTo("old");
            assertThat(entity.getMimetype()).isEqualTo("image/jpeg");
            assertThat(((ImageFileEntity) entity).getWidth()).isEqualTo(32);
            assertThat(entity.getGpsLocation()).isNotNull();
            verify(fileRepository).save(entity);
            verify(metadataRepository).deleteAll(oldMetadata);
            verify(fileTagRepository).deleteAll(oldFileTags);
            verify(fileTagRepository).save(any(FileTag.class));
            verify(metadataRepository).saveAll(any());
        }

        @Test
        void refreshEnrichesAnExistingGpsLocation() throws Exception {
            var entity = stored(new ImageFileEntity(), FileTypeEnum.IMAGE, "a.jpg");
            var existing = new GpsLocationEntity();
            existing.setId(8L);
            when(gpsLocationRepository.findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(any(), eq('N'), any(), eq('E')))
                .thenReturn(Optional.of(existing));
            when(gpsLocationRepository.save(existing)).thenReturn(existing);
            var gps = new JSONObject().put("GPSLatitudeRef", "N")
                .put("GPSLatitude", "60 deg 10' 12.00\"")
                .put("GPSLongitudeRef", "E")
                .put("GPSLongitude", "24 deg 56' 24.00\"");
            var json = metadataJson(null, group("GPS", gps), group("XMP-photoshop", new JSONObject().put("City", "Turku")
                .put("State", "VS")
                .put("Country", "FI")), group("XMP-iptcCore", new JSONObject().put("Location", "Aura")));
            try (var ignored = mockTool(json)) {
                assertThat(service.refreshExistingOriginalFile(entity, write("a.jpg"))).isTrue();
            }
            assertThat(entity.getGpsLocation()).isSameAs(existing);
            assertThat(existing.getCity()).isEqualTo("Turku");
            assertThat(existing.getStreet()).isEqualTo("Aura");

            // A complete location is reused without saving it again
            var complete = new GpsLocationEntity();
            complete.setCountry("FI");
            complete.setCity("Turku");
            complete.setState("VS");
            complete.setStreet("Aura");
            complete.setSubLocation("Centre");
            when(gpsLocationRepository.findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(any(), eq('N'), any(), eq('E')))
                .thenReturn(Optional.of(complete));
            try (var ignored = mockTool(json)) {
                assertThat(service.refreshExistingOriginalFile(entity, write("a.jpg"))).isTrue();
            }
            assertThat(entity.getGpsLocation()).isSameAs(complete);
            verify(gpsLocationRepository, never()).save(complete);

            // Coordinates without a longitude reference are dropped
            try (var ignored = mockTool(metadataJson(null, group("GPS", new JSONObject().put("GPSLatitudeRef", "N")
                .put("GPSLatitude", "60 deg 10' 12.00\""))))) {
                assertThat(service.refreshExistingOriginalFile(entity, write("a.jpg"))).isTrue();
            }
            assertThat(entity.getGpsLocation()).isNull();
        }

        @Test
        void refreshesTheTypeSpecificFieldsOfEveryType() throws Exception {
            record Case(FileEntity entity, FileTypeEnum type, String name, String mimetype, JSONObject[] groups) {}
            // MUSIC is absent on purpose: a refresh resolves the type from the MIME type, which only ever yields AUDIO, so a music file
            // never passes the type check of refreshExistingOriginalFile
            var cases = List.of(new Case(new ArchiveFileEntity(), FileTypeEnum.ARCHIVE, "r.zip", null, new JSONObject[0]),
                    new Case(new AudioFileEntity(), FileTypeEnum.AUDIO, "r.wav", null, new JSONObject[0]),
                    new Case(new BinaryFileEntity(), FileTypeEnum.BINARY, "r.bin", null,
                            new JSONObject[]{ group("XMP-xmp", new JSONObject().put("Label", "Fw 3")) }),
                    new Case(new DataFileEntity(), FileTypeEnum.DATA, "r.json", null, new JSONObject[0]),
                    new Case(new DocumentFileEntity(), FileTypeEnum.DOCUMENT, "r.pdf", null, new JSONObject[0]),
                    new Case(new ExecutableFileEntity(), FileTypeEnum.EXECUTABLE, "r.sh", null, new JSONObject[0]),
                    new Case(new FontFileEntity(), FileTypeEnum.FONT, "r.ttf", null, new JSONObject[0]),
                    new Case(new IconFileEntity(), FileTypeEnum.ICON, "r.ico", null, new JSONObject[0]),
                    new Case(new InteractiveFileEntity(), FileTypeEnum.INTERACTIVE, "r.dir", null, new JSONObject[0]),
                    new Case(new ThumbFileEntity(), FileTypeEnum.THUMB, "r.thumb", "image/x-thumbnail",
                            new JSONObject[]{ group("XMP-xmpMM", new JSONObject().put("OriginalDocumentID", "doc-t")) }),
                    new Case(new VectorFileEntity(), FileTypeEnum.VECTOR, "r.svg", null, new JSONObject[0]),
                    new Case(new VideoFileEntity(), FileTypeEnum.VIDEO, "r.mp4", null, new JSONObject[0]));
            var target = new ImageFileEntity();
            when(fileRepository.findByOriginalDocumentId("doc-t")).thenReturn(target);

            for (var testCase : cases) {
                var entity = stored(testCase.entity(), testCase.type(), testCase.name());
                try (var ignored = mockTool(metadataJson(testCase.mimetype(), testCase.groups()))) {
                    assertThat(service.refreshExistingOriginalFile(entity, write(testCase.name()))).as(testCase.name())
                        .isTrue();
                }
                assertThat(entity.getFileType()).isEqualTo(testCase.type());
            }

            assertThat(((ArchiveFileEntity) cases.get(0)
                .entity()).getUncompressedSize()).isEqualTo(600L);
            assertThat(((AudioFileEntity) cases.get(1)
                .entity()).getChannels()).isEqualTo(2);
            assertThat(((BinaryFileEntity) cases.get(2)
                .entity()).getSoftwareMajorVersion()).isEqualTo(3);
            assertThat(((DataFileEntity) cases.get(3)
                .entity()).getDataStructure()).isEqualTo("JSON");
            assertThat(((DocumentFileEntity) cases.get(4)
                .entity()).getPageCount()).isEqualTo(3);
            assertThat(((ExecutableFileEntity) cases.get(5)
                .entity()).isScript()).isTrue();
            assertThat(((FontFileEntity) cases.get(6)
                .entity()).getFontFamily()).isEqualTo("Lato");
            assertThat(((IconFileEntity) cases.get(7)
                .entity()).getIsScalable()).isTrue();
            assertThat(((InteractiveFileEntity) cases.get(8)
                .entity()).getTechnology()).isEqualTo("SHOCKWAVE");
            assertThat(((ThumbFileEntity) cases.get(9)
                .entity()).getTargetFile()).isSameAs(target);
            assertThat(((VectorFileEntity) cases.get(10)
                .entity()).getLayersCount()).isEqualTo(4);
            assertThat(((VideoFileEntity) cases.get(11)
                .entity()).getCodec()).isEqualTo("h264");
            verify(fileRepository, org.mockito.Mockito.times(cases.size())).save(any(FileEntity.class));
        }
    }

    @Nested
    @DisplayName("processExportDirectory")
    class ExportDirectory {

        private Path exportDir;

        @BeforeEach
        void exportDirectory() throws IOException {
            exportDir = Files.createDirectories(tempDir.resolve("export"));
        }

        private File export(String name) throws IOException {
            var file = exportDir.resolve(name);
            Files.writeString(file, "export " + name);
            return file.toFile();
        }

        @Test
        void registersNewExportsAndReportsOrphansAndKnownFiles() throws Exception {
            export("new.jpg");
            export("orphan.jpg");
            export("registered.jpg");
            var original = new ImageFileEntity();
            original.setId(4L);
            when(exportFileRepository.findByFilePathAndFilename(any(), any())).thenReturn(Optional.empty());
            when(exportedFilesService.existsByOriginalDocumentId("doc-new")).thenReturn(false);
            when(exportedFilesService.existsByOriginalDocumentId("doc-orphan")).thenReturn(false);
            when(exportedFilesService.existsByOriginalDocumentId("doc-registered")).thenReturn(true);
            when(exportedFilesService.existsByPathAndFilename(any(), any())).thenReturn(false);
            when(fileRepository.findByOriginalDocumentId("doc-new")).thenReturn(original);
            when(fileRepository.findByOriginalDocumentId("doc-orphan")).thenReturn(null);
            when(exportedFilesService.save(any(ExportFileEntity.class))).thenAnswer(invocation -> {
                var entity = invocation.getArgument(0, ExportFileEntity.class);
                entity.setId(11L);
                return entity;
            });
            var recorder = org.mockito.Mockito.mock(ScanRecorder.class);
            var orphans = new ArrayList<String>();
            var responses = new ArrayList<ExportFileResponse>();
            var errors = new StringBuilder();

            List<Long> result;
            doAnswer(invocation -> {
                var name = invocation.getArgument(0, File.class)
                    .getName()
                    .replace(".jpg", "");
                return metadataJson("image/jpeg", group("XMP-xmpMM", new JSONObject().put("OriginalDocumentID", "doc-" + name)));
            }).when(exifToolService)
                .readMetadataJson(any());
            {
                result = service.processExportDirectory(exportDir, errors, orphans, responses, recorder);
            }

            assertThat(result).containsExactly(3L, 2L);
            assertThat(orphans).containsExactly("orphan.jpg");
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst()
                .getFilename()).isEqualTo("new.jpg");
            assertThat(responses.getFirst()
                .getFileId()).isEqualTo(4L);
            assertThat(errors).isEmpty();
            verify(recorder, org.mockito.Mockito.times(3)).checkpoint();
            verify(recorder).exportFileCreated(any(ExportFileEntity.class));
            var saved = ArgumentCaptor.forClass(ExportFileEntity.class);
            verify(exportedFilesService).save(saved.capture());
            assertThat(saved.getValue()
                .getFilePath()).isEqualTo("/export");
            assertThat(saved.getValue()
                .getMimetype()).isEqualTo("image/jpeg");
            assertThat(saved.getValue()
                .getOriginalDocumentId()).isEqualTo("doc-new");
        }

        @Test
        void replacesAChangedExportWithoutReportingItAsCreated() throws Exception {
            export("changed.jpg");
            var stale = ExportFileEntity.builder()
                .id(3L)
                .sha256sum("stale")
                .build();
            when(exportFileRepository.findByFilePathAndFilename("/export", "changed.jpg")).thenReturn(Optional.of(stale));
            when(exportedFilesService.existsByOriginalDocumentId(any())).thenReturn(false);
            when(exportedFilesService.existsByPathAndFilename(any(), any())).thenReturn(true);
            var recorder = org.mockito.Mockito.mock(ScanRecorder.class);

            List<Long> result;
            try (var ignored = mockTool(metadataJson("image/jpeg"))) {
                result = service.processExportDirectory(exportDir, new StringBuilder(), new ArrayList<>(), new ArrayList<>(), recorder);
            }

            assertThat(result).containsExactly(1L, 0L);
            verify(exportFileRepository).delete(stale);
            verify(recorder, never()).exportFileCreated(any());
        }

        @Test
        void metadataFailureAbortsTheDirectory() throws Exception {
            export("bad.jpg");
            when(exportFileRepository.findByFilePathAndFilename(any(), any())).thenReturn(Optional.empty());
            var errors = new StringBuilder();
            try (var ignored = mockToolFailure()) {
                assertThatThrownBy(() -> service.processExportDirectory(exportDir, errors, new ArrayList<>(), new ArrayList<>()))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IOException.class);
            }
            assertThat(errors).contains("Failed to extract metadata from exported file");
        }

        @Test
        void emptyDirectoryReturnsZeroCounts() {
            assertThat(service.processExportDirectory(exportDir, new StringBuilder(), new ArrayList<>(), new ArrayList<>())).containsExactly(0L, 0L);
        }
    }
}
