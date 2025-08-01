package fi.poltsi.vempain.file.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public interface BaseRestAPI<T> {

	@GetMapping("")
	ResponseEntity<List<T>> findAll();

	@GetMapping("/{id}")
	ResponseEntity<T> findById(@PathVariable("id") long id);

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
