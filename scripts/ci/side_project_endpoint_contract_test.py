import unittest
from scripts.ci.validate_side_project_endpoint_contracts import validate


class SideProjectEndpointContractTest(unittest.TestCase):
    def test_critical_endpoint_contracts_are_aligned(self) -> None:
        self.assertEqual([], validate())


if __name__ == "__main__":
    unittest.main()
