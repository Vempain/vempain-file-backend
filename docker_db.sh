#!/usr/bin/env bash

setup_database_container() {
    local db_name="$1"
    local db_user="$2"
    local db_password="$3"
    local port="$4"

    local container_name="dev_${db_name}"
    local volume_name="${container_name}_volume"

    docker rm -f "${container_name}"
    docker volume rm "${volume_name}"

    docker run -d -p "${port}:5432" -v "${volume_name}:/var/lib/postgresql/data/" \
        -e "POSTGRES_HOST_AUTH_METHOD=trust" \
        --name "${container_name}" postgres:16

    echo "Waiting for postgres to start..."
    until docker exec "${container_name}" psql --host=localhost --port=5432 --username postgres -c '\l' > /dev/null 2>&1; do
        sleep 0.5
    done

    echo "Creating database ${db_name}..."
    docker exec -it "${container_name}" createdb "${db_name}" -U postgres

    echo "Creating user ${db_user}..."
    docker exec -it "${container_name}" psql -U postgres -c "CREATE USER ${db_user} WITH ENCRYPTED PASSWORD '${db_password}';"

    echo "Granting privileges to user ${db_user} on database ${db_name}..."
    docker exec -it "${container_name}" psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE ${db_name} TO ${db_user};"

    echo "Set user ${db_user} as owner on database ${db_name}..."
    docker exec -it "${container_name}" psql -U postgres -c "ALTER DATABASE ${db_name} OWNER TO ${db_user};"

    echo "Database setup for ${db_name} completed."
}

# Setup first database for vempain_file
setup_database_container "vempain_file" "vempain_file" "vempain_file_password" 5432

echo "All databases setup completed."
