import json
import xml.etree.ElementTree as ET
from pathlib import Path
suites=[ET.parse(p).getroot() for p in Path('app/build/test-results').rglob('TEST-*.xml')]
assert suites, 'Missing actual JUnit reports'
tests=[case for suite in suites for case in suite.iter('testcase')]
assert tests and all(case.find('failure') is None and case.find('error') is None for case in tests)
print(json.dumps({'passed':len(tests),'scenarios':[case.get('name') for case in tests]},sort_keys=True))
