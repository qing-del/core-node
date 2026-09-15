package com.jacolp.middleware.config;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MapperXmlClasspathTest {

    private static final String MAPPER_LOCATION_PATTERN = "classpath*:mapper/**/*.xml";
    private static final int CURRENT_MAPPER_BASELINE = 17;

    @Test
    void mapperLocationsUseMultiJarPatternAndEveryMapperXmlParses() throws Exception {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties)
                .isNotNull()
                .containsEntry("mybatis.mapper-locations", MAPPER_LOCATION_PATTERN);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] mapperResources = resolver.getResources(MAPPER_LOCATION_PATTERN);

        assertThat(mapperResources).hasSizeGreaterThanOrEqualTo(CURRENT_MAPPER_BASELINE);
        assertThat(Arrays.stream(mapperResources).map(Resource::getFilename))
                .contains("UserMapper.xml", "NoteMapper.xml", "ImageMapper.xml", "MetaAuditMapper.xml");

        Configuration configuration = new Configuration();
        for (Resource mapperResource : mapperResources) {
            try (InputStream inputStream = mapperResource.getInputStream()) {
                new XMLMapperBuilder(
                        inputStream,
                        configuration,
                        mapperResource.getDescription(),
                        configuration.getSqlFragments())
                        .parse();
            }
        }

        assertThat(configuration.getMappedStatementNames()).isNotEmpty();
    }
}
