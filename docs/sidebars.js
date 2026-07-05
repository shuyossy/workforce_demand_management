module.exports = {
  docs: [
    'intro',
    {
      type: 'category',
      label: 'はじめに',
      items: [
        'getting-started/prerequisites',
        'getting-started/env-setup',
        'getting-started/first-run',
        'getting-started/troubleshooting',
        'getting-started/strip-sample',
      ],
    },
    {
      type: 'category',
      label: '要件',
      items: [
        'requirements/functional',
        'requirements/non-functional',
        'requirements/domain-glossary',
      ],
    },
    {
      type: 'category',
      label: 'ADR',
      items: [
        'adrs/ADR-001-clean-hexagonal',
        'adrs/ADR-003-auth-junction-path',
        'adrs/ADR-004-coverage-by-layer',
        'adrs/ADR-005-no-js-bundling',
      ],
    },
    {
      type: 'category',
      label: 'ドメイン設計',
      items: [
        'domain-design/domain-model',
        'domain-design/usecases',
        'domain-design/business-rules',
      ],
    },
    'technical-design',
    {
      type: 'category',
      label: '運用',
      items: [
        'operations/wildfly-cli',
        'operations/flyway',
        'operations/docker',
        'operations/ci-cd',
      ],
    },
    {
      type: 'category',
      label: '品質',
      items: ['quality/static-analysis', 'quality/reports'],
    },
  ],
};
