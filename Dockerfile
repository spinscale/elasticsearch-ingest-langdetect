FROM docker.elastic.co/elasticsearch/elasticsearch:8.5.0

ADD build/distribution/elasticsearch-ingest-langdetect.zip /elasticsearch-ingest-langdetect.zip
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch file:///elasticsearch-ingest-langdetect.zip
EXPOSE 9200
