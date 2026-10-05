package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily repair of files whose ACL link is missing. Every file must reference an ACL; until it does the file is denied for everyone.
 * For each file without an assigned {@code acl_id}, or whose {@code acl_id} has no ACL rows, a new ACL granting all privileges to the
 * file's creator is created and linked to the file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAclRepairSchedule {

	private final FileRepository fileRepository;
	private final AclService     aclService;

	@Value("${vempain.acl-repair.enabled:true}")
	private boolean schedulerEnabled;

	@Value("${vempain.scheduling.enabled:true}")
	private boolean schedulingEnabled = true;

	@Scheduled(cron = "${vempain.acl-repair.cron:0 30 3 * * *}")
	public void repairMissingAclsScheduled() {
		if (!schedulingEnabled || !schedulerEnabled) {
			return;
		}

		repairMissingAcls();
	}

	/**
	 * @return the number of files that received a new ACL
	 */
	@Transactional
	public long repairMissingAcls() {
		var files    = fileRepository.findFilesWithoutAcl();
		var repaired = 0L;

		if (files.isEmpty()) {
			log.debug("All files have a usable ACL");
			return repaired;
		}

		log.info("Found {} files without a usable ACL", files.size());

		for (var file : files) {
			if (file.getCreator() == null) {
				log.error("File {} has no creator, cannot create an ACL for it", file.getId());
				continue;
			}

			try {
				var acl = aclService.createUniqueAcl(file.getCreator(), null, true, true, true, true);
				file.setAclId(acl.getAclId());
				fileRepository.save(file);
				repaired++;
				log.info("Created ACL {} for file {} owned by user {}", acl.getAclId(), file.getId(), file.getCreator());
			} catch (VempainAclException e) {
				log.error("Failed to create an ACL for file {} owned by user {}: {}", file.getId(), file.getCreator(), e.getMessage());
			}
		}

		return repaired;
	}
}
