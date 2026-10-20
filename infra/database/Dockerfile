# The official PostgreSQL image without gosu (issue #58).
#
# The upstream entrypoint starts as root, fixes data directory ownership and then
# re-executes itself through gosu as the postgres user. gosu is built by upstream with
# an old Go toolchain and carries most of the image's scanner findings. Starting as the
# postgres user skips that path entirely, so the binary is never needed: the data
# directory is already owned by postgres in existing volumes and in fresh named volumes,
# which copy the image's ownership. Same Debian base and PostgreSQL major version as
# before, so existing data directories need no migration.
FROM postgres:17
RUN rm /usr/local/bin/gosu
USER postgres
