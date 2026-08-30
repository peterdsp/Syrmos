"""Tests for the honest live-vehicle service status (purple-cluster fix)."""
import unittest

from syrmos_admin.vehicle_status import (
    annotate,
    derive_status,
    is_boardable,
    near_depot,
    show_on_passenger_map,
)

# SKA Acharnon yard, from seed_greek_corridors.py.
SKA = (38.054188, 23.732645)
SYNTAGMA = (37.975379, 23.735368)  # central Athens, not a depot


class DeriveStatusTest(unittest.TestCase):
    def test_assigned_moving_is_in_service(self):
        self.assertEqual(derive_status(line_id="A1", speed=60, lat=38.0, lng=23.8), "in_service")

    def test_assigned_stationary_is_in_service_not_hidden(self):
        # A real A-line train dwelling at a station at 0 km/h must stay boardable.
        s = derive_status(line_id="A2", speed=0, lat=SKA[0], lng=SKA[1])
        self.assertEqual(s, "in_service")
        self.assertTrue(show_on_passenger_map(s))

    def test_intercity_is_in_service(self):
        self.assertEqual(derive_status(line_id="IC", speed=0, lat=38.9, lng=22.4), "in_service")

    def test_unassigned_moving_is_position_only(self):
        s = derive_status(line_id="P", speed=25, lat=38.0, lng=23.8)
        self.assertEqual(s, "position_only")
        self.assertTrue(show_on_passenger_map(s))   # visible, but ...
        self.assertFalse(is_boardable(s))           # ... not boardable

    def test_unassigned_stationary_in_depot_is_parked_yard(self):
        s = derive_status(line_id="P", speed=0, lat=SKA[0], lng=SKA[1])
        self.assertEqual(s, "parked_yard")
        self.assertFalse(show_on_passenger_map(s))  # withheld from the map
        self.assertFalse(is_boardable(s))

    def test_unassigned_stationary_off_depot_is_not_in_service(self):
        s = derive_status(line_id="P", speed=0, lat=SYNTAGMA[0], lng=SYNTAGMA[1])
        self.assertEqual(s, "not_in_service")
        self.assertFalse(show_on_passenger_map(s))

    def test_unassigned_unknown_speed_shown_not_hidden(self):
        # No speed datum: do not assume parked -> keep visible as position_only.
        self.assertEqual(derive_status(line_id="P", speed=None, lat=SKA[0], lng=SKA[1]),
                         "position_only")

    def test_stationary_speed_boundary(self):
        self.assertEqual(derive_status(line_id="P", speed=1.0, lat=SYNTAGMA[0], lng=SYNTAGMA[1]),
                         "not_in_service")
        self.assertEqual(derive_status(line_id="P", speed=1.1, lat=SYNTAGMA[0], lng=SYNTAGMA[1]),
                         "position_only")


class DepotTest(unittest.TestCase):
    def test_ska_is_a_depot(self):
        self.assertTrue(near_depot(*SKA))

    def test_central_athens_is_not_a_depot(self):
        self.assertFalse(near_depot(*SYNTAGMA))

    def test_zero_island_is_not_a_depot(self):
        self.assertFalse(near_depot(0.0, 0.0))

    def test_missing_coords_safe(self):
        self.assertFalse(near_depot(None, None))


class AnnotateTest(unittest.TestCase):
    def test_annotate_sets_fields(self):
        t = annotate({"lineId": "A1", "speed": 40, "lat": 38.0, "lng": 23.8})
        self.assertEqual(t["status"], "in_service")
        self.assertTrue(t["inService"])

    def test_annotate_parked(self):
        t = annotate({"lineId": "P", "speed": 0, "lat": SKA[0], "lng": SKA[1]})
        self.assertEqual(t["status"], "parked_yard")
        self.assertFalse(t["inService"])


if __name__ == "__main__":
    unittest.main()
