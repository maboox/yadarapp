# The server is one static binary with its own root certificates, so the image needs no base image
# and nothing is downloaded while deploying (works from inside Iran).
FROM scratch
COPY yadar-server /yadar-server
ENV TMPDIR=/pb_data
EXPOSE 8080
ENTRYPOINT ["/yadar-server", "serve", "--http=0.0.0.0:8080", "--dir=/pb_data"]
