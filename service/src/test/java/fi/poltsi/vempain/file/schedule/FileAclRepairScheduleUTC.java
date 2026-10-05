package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAclRepairScheduleUTC {
	@Mock
	private FileRepository fileRepository;
	@Mock
	private AclService     aclService;

	@InjectMocks
	private FileAclRepairSchedule schedule;

	@Test
	void nothingToRepairReturnsZero() {
		when(fileRepository.findFilesWithoutAcl()).thenReturn(List.of());

		assertThat(schedule.repairMissingAcls()).isZero();
		verifyNoInteractions(aclService);
	}

	@Test
	void fileWithoutAclGetsAnAclForItsCreator() throws VempainAclException {
		var file = ImageFileEntity.builder()
								  .id(10L)
								  .aclId(0L)
								  .creator(5L)
								  .build();
		when(fileRepository.findFilesWithoutAcl()).thenReturn(List.of(file));
		when(aclService.createUniqueAcl(eq(5L), isNull(), eq(true), eq(true), eq(true), eq(true)))
				.thenReturn(Acl.builder()
							   .aclId(77L)
							   .userId(5L)
							   .build());

		assertThat(schedule.repairMissingAcls()).isEqualTo(1L);
		assertThat(file.getAclId()).isEqualTo(77L);
		verify(fileRepository).save(file);
	}

	@Test
	void fileWithoutCreatorIsSkipped() throws VempainAclException {
		var file = ImageFileEntity.builder()
								  .id(11L)
								  .aclId(0L)
								  .build();
		when(fileRepository.findFilesWithoutAcl()).thenReturn(List.of(file));

		assertThat(schedule.repairMissingAcls()).isZero();
		verify(aclService, never()).createUniqueAcl(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
		verify(fileRepository, never()).save(any());
	}

	@Test
	void aclCreationFailureIsLoggedAndSkipped() throws VempainAclException {
		var file = ImageFileEntity.builder()
								  .id(12L)
								  .aclId(0L)
								  .creator(5L)
								  .build();
		when(fileRepository.findFilesWithoutAcl()).thenReturn(List.of(file));
		when(aclService.createUniqueAcl(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
				.thenThrow(new VempainAclException("Invalid user ID given in the ACL"));

		assertThat(schedule.repairMissingAcls()).isZero();
		assertThat(file.getAclId()).isZero();
		verify(fileRepository, never()).save(any());
	}

	@Test
	void scheduledRunHonoursTheEnabledFlags() {
		ReflectionTestUtils.setField(schedule, "schedulingEnabled", true);
		ReflectionTestUtils.setField(schedule, "schedulerEnabled", false);
		schedule.repairMissingAclsScheduled();
		verifyNoInteractions(fileRepository);

		ReflectionTestUtils.setField(schedule, "schedulingEnabled", false);
		ReflectionTestUtils.setField(schedule, "schedulerEnabled", true);
		schedule.repairMissingAclsScheduled();
		verifyNoInteractions(fileRepository);

		ReflectionTestUtils.setField(schedule, "schedulingEnabled", true);
		when(fileRepository.findFilesWithoutAcl()).thenReturn(List.of());
		schedule.repairMissingAclsScheduled();
		verify(fileRepository).findFilesWithoutAcl();
	}
}
