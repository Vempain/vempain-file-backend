# Vempain File CLI

Command-line client for the Vempain File backend API.

## Features implemented

- Login with backend URL + username + password, store JWT session locally
- File-type specific listing via `PagedRequest`
- Deterministic file show routing: `--type` + `--id`
- Metadata + content display (text rendered, binary summarized)
- Formatted tabular output for file listings with truncation for long values
- `file-show` supports `--raw` and `--content-limit <n>`
- Data publish commands (music + gps-time-series)
- Scan command for original/export directories
- Interactive shell with tab completion:
    - file types in lowercase
    - backend path completion for scan directories

## Build

```bash
cd /home/poltsi/Work/Vempain/vempain-file-backend
./gradlew :cli:fatJar
```

## Run

```bash
java -jar vf-cli.jar --help
```

## Usage examples

```bash
java -jar vf-cli.jar login --url http://localhost:8080/api --username admin --password qwerty
java -jar vf-cli.jar files-list --type music --page 0 --size 25 --sort-by created --direction DESC
java -jar vf-cli.jar file-show --type music --id 42
java -jar vf-cli.jar file-show --type music --id 42 --content-limit 2048
java -jar vf-cli.jar file-show --type music --id 42 --raw
java -jar vf-cli.jar publish-music
java -jar vf-cli.jar scan --original-directory /music
java -jar vf-cli.jar shell
```

## Session storage

Session is stored at:

- `~/.config/vempain-file-cli/session.json`

