package fi.poltsi.vempain.file.processing;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileQueueProcessorFactoryUTC {
	@Test
	void returnsProcessorForRegisteredType() {
		var processor = mock(FileQueueProcessor.class);
		when(processor.supportedType()).thenReturn(FileTypeEnum.VIDEO);

		var factory = new FileQueueProcessorFactory(List.of(processor));

		assertThat(factory.processorFor(FileTypeEnum.VIDEO)).isSameAs(processor);
	}

	@Test
	void rejectsUnsupportedType() {
		var factory = new FileQueueProcessorFactory(List.of());

		assertThatIllegalArgumentException().isThrownBy(() -> factory.processorFor(FileTypeEnum.IMAGE));
	}
}
