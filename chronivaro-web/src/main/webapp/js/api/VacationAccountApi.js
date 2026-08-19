import Rest from '../utils/Rest.js';

export default class VacationAccountApi {

    static async getMyVacationAccount(year) {
        const url = `rest/chronivaro/v1/me/vacation-account${year ? '?year=' + year : ''}`;
        return await Rest.get(url);
    }
}
