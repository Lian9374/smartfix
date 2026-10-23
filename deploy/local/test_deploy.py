import copy
import unittest
from deploy import REPO, WORKFLOW, require_trusted_run

class DeploymentGateTests(unittest.TestCase):
    def setUp(self):
        self.run = dict(event='push', head_branch='main', head_sha='verified',
                        status='completed', conclusion='success', path=WORKFLOW,
                        head_repository=dict(full_name=REPO))

    def test_current_main_success_is_accepted(self):
        require_trusted_run(self.run, 'verified')

    def test_untrusted_failed_stale_and_other_workflow_runs_are_refused(self):
        for key, value in dict(event='pull_request', head_branch='feature/untrusted',
                               head_sha='outdated', status='in_progress', conclusion='failure',
                               path='.github/workflows/swe5001-ci.yml').items():
            with self.subTest(field=key):
                candidate = copy.deepcopy(self.run)
                candidate[key] = value
                with self.assertRaises(ValueError):
                    require_trusted_run(candidate, 'verified')

    def test_fork_source_is_refused(self):
        self.run['head_repository']['full_name'] = 'someone/smartfix'
        with self.assertRaises(ValueError):
            require_trusted_run(self.run, 'verified')

if __name__ == '__main__':
    unittest.main()
