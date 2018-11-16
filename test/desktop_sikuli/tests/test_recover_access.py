import pytest

from tests import base_user
from tests.base_test_case import BaseTestCase
from views.sign_in_view import SignInView


class TestRecoverAccess(BaseTestCase):

    def test_recover_access(self):
        sign_in = SignInView()
        sign_in.i_have_account_button.click()
        sign_in.recovery_phrase_input.send_keys(base_user['passphrase'])
        sign_in.recover_password_input.send_keys('123456')
        sign_in.sign_in_button.click()
        sign_in.home_button.find_element()
        profile = sign_in.profile_button.click()
        profile.share_my_code_button.click()
        profile.find_text(base_user['public_key'])

    @pytest.mark.testrail_id(5571)
    def test_recover_access_proceed_with_enter(self):
        sign_in = SignInView()
        sign_in.i_have_account_button.click()
        sign_in.recovery_phrase_input.send_keys(base_user['passphrase'])
        sign_in.press_tab()
        sign_in.recover_password_input.verify_is_focused()
        sign_in.recover_password_input.input_value('123456')
        sign_in.press_enter()
        sign_in.home_button.find_element()

    @pytest.mark.testrail_id(5569)
    def test_recover_access_go_back(self):
        sign_in = SignInView()
        sign_in.i_have_account_button.click()
        sign_in.recovery_phrase_input.find_element()
        sign_in.back_button.click()
        sign_in.create_account_button.find_element()

    @pytest.mark.testrail_id(5649)
    def test_recovery_phrase_for_recovered_account(self):
        sign_in = SignInView()
        sign_in.recover_access(base_user['passphrase'])
        profile = sign_in.profile_button.click()
        if profile.share_my_code_button.is_visible():
            profile.element_by_text('Backup').verify_element_is_not_present()
        else:
            pytest.fail('Profile view was not opened')
